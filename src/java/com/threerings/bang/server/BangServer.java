//
// $Id$

package com.threerings.bang.server;

import java.io.File;
import java.util.Iterator;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import com.samskivert.jdbc.ConnectionProvider;
import com.samskivert.jdbc.StaticConnectionProvider;
import com.samskivert.jdbc.TransitionRepository;
import com.samskivert.depot.PersistenceContext;

import com.samskivert.util.AuditLogger;
import com.samskivert.util.Interval;
import com.samskivert.util.Invoker;

import com.threerings.admin.server.AdminProvider;
import com.threerings.admin.server.ConfigRegistry;
import com.threerings.admin.server.DatabaseConfigRegistry;
import com.threerings.cast.ComponentRepository;
import com.threerings.cast.bundle.BundledComponentRepository;
import com.threerings.resource.ResourceManager;
import com.threerings.util.Name;

import com.threerings.presents.data.ClientObject;
import com.threerings.presents.net.AuthRequest;
import com.threerings.presents.server.Authenticator;
import com.threerings.presents.server.SessionFactory;
import com.threerings.presents.server.ClientManager;
import com.threerings.presents.server.ClientResolver;
import com.threerings.presents.server.InvocationManager;
import com.threerings.presents.server.PresentsSession;
import com.threerings.presents.server.PresentsDObjectMgr;
import com.threerings.presents.server.ReportManager;
import com.threerings.presents.server.net.ConnectionManager;

import com.threerings.crowd.chat.server.ChatProvider;
import com.threerings.crowd.server.BodyLocator;
import com.threerings.crowd.server.CrowdServer;
import com.threerings.crowd.server.LocationManager;
import com.threerings.crowd.server.PlaceRegistry;

import com.threerings.parlor.server.ParlorManager;

import com.threerings.user.AccountActionRepository;

import com.threerings.bang.avatar.data.AvatarCodes;
import com.threerings.bang.avatar.data.BarberConfig;
import com.threerings.bang.avatar.server.BarberManager;
import com.threerings.bang.avatar.server.persist.LookRepository;
import com.threerings.bang.avatar.util.AvatarLogic;

import com.threerings.bang.admin.server.BangAdminManager;
import com.threerings.bang.admin.server.RuntimeConfig;
import com.threerings.bang.bank.data.BankConfig;
import com.threerings.bang.bank.server.BankManager;
import com.threerings.bang.bounty.data.OfficeConfig;
import com.threerings.bang.bounty.server.OfficeManager;
import com.threerings.bang.bounty.server.persist.BountyRepository;
import com.threerings.bang.chat.server.BangChatProvider;
import com.threerings.bang.gang.data.HideoutConfig;
import com.threerings.bang.gang.server.GangManager;
import com.threerings.bang.gang.server.HideoutManager;
import com.threerings.bang.gang.server.persist.GangRepository;
import com.threerings.bang.ranch.data.RanchConfig;
import com.threerings.bang.ranch.server.RanchManager;
import com.threerings.bang.saloon.data.SaloonConfig;
import com.threerings.bang.saloon.server.SaloonManager;
import com.threerings.bang.station.data.StationConfig;
import com.threerings.bang.station.server.StationManager;
import com.threerings.bang.store.data.StoreConfig;
import com.threerings.bang.store.server.StoreManager;
import com.threerings.bang.tourney.server.BangTourniesManager;

import com.threerings.bang.data.PlayerObject;
import com.threerings.bang.data.TownObject;
import com.threerings.bang.server.persist.BangStatRepository;
import com.threerings.bang.server.persist.ItemRepository;
import com.threerings.bang.server.persist.PlayerRepository;
import com.threerings.bang.server.persist.RatingRepository;
import com.threerings.bang.util.DeploymentConfig;

import static com.threerings.bang.Log.log;

/**
 * Creates and manages all the services needed on the bang server.
 */
public class BangServer extends CrowdServer
{
    /** Configures dependencies needed by the Bang server. */
    public static class Module extends CrowdServer.Module
    {
        @Override protected void configure () {
            super.configure();
            ConnectionProvider conprov = new StaticConnectionProvider(ServerConfig.getJDBCConfig());
            bind(ConnectionProvider.class).toInstance(conprov);
            bind(PersistenceContext.class).toInstance(
                new PersistenceContext("bangdb", conprov, null));
            bind(ReportManager.class).to(BangReportManager.class);
            bind(ChatProvider.class).to(BangChatProvider.class);
            bind(Authenticator.class).to(ServerConfig.getAuthenticator());
            bind(BodyLocator.class).to(PlayerLocator.class);
        }
    }

    /** The connection provider used to obtain access to our JDBC databases. */
    public static ConnectionProvider conprov;

    /** Used to provide database access to our Depot repositories. */
    public static PersistenceContext perCtx;

    /** Used to coordinate transitions to persistent data. */
    public static TransitionRepository transitrepo;

    /** A resource manager with which we can load resources in the same manner that the client does
     * (for resources that are used on both the server and client). */
    public static ResourceManager rsrcmgr;

    /** Maintains a registry of runtime configuration information. */
    public static ConfigRegistry confreg;

    /** Provides information on our character components. */
    public static ComponentRepository comprepo;

    /** Handles the heavy lifting relating to avatar looks and articles. */
    public static AvatarLogic alogic;

    /** Any database actions that involve the authentication database <em>must</em> be run on this
     * invoker to avoid blocking normal game database actions. */
    public static Invoker authInvoker;

    /** A reference to the authenticator in use by the server. */
    public static BangAuthenticator author;

    /** Communicates with the other servers in our cluster. */
    public static BangPeerManager peermgr;

    /** Handles the processing of account actions. */
    public static AccountActionManager actionmgr;

    /** Manages global player related bits. */
    public static PlayerManager playmgr;

    /** Manages gangs. */
    public static GangManager gangmgr;

    /** Manages tournaments. */
    public static BangTourniesManager tournmgr;

    /** Manages rating bits. */
    public static RatingManager ratingmgr;

    /** Manages the persistent repository of player data. */
    public static PlayerRepository playrepo;

    /** Manages the persistent repository of gang data. */
    public static GangRepository gangrepo;

    /** Manages the persistent repository of items. */
    public static ItemRepository itemrepo;

    /** Manages the persistent repository of stats. */
    public static BangStatRepository statrepo;

    /** Manages the persistent repository of ratings. */
    public static RatingRepository ratingrepo;

    /** Manages the persistent repository of avatar looks. */
    public static LookRepository lookrepo;

    /** Tracks bounty related persistent statistics. */
    public static BountyRepository bountyrepo;

    /** Provides micropayment services. (This will need to be turned into a
     * pluggable interface to support third party micropayment systems.) */
    public static BangCoinManager coinmgr;

    /** Manages the market for exchange between scrips and coins. */
    public static BangCoinExchangeManager coinexmgr;

    /** Manages administrative services. */
    public static BangAdminManager adminmgr;

    /** Keeps an eye on the Ranch, a good man to have around. */
    public static RanchManager ranchmgr;

    /** Manages the Saloon and match-making. */
    public static SaloonManager saloonmgr;

    /** Manages the General Store and item purchase. */
    public static StoreManager storemgr;

    /** Manages the Bank and the coin exchange. */
    public static BankManager bankmgr;

    /** Manages the Barber and avatar customization. */
    public static BarberManager barbermgr;

    /** Manages the Train Station and inter-town travel. */
    public static StationManager stationmgr;

    /** Manages the Hideout and Gangs. */
    public static HideoutManager hideoutmgr;

    /** Manages the Sheriff's Office and Bounties. */
    public static OfficeManager officemgr;

    /** Manages our selection of game boards. */
    public static BoardManager boardmgr = new BoardManager();

    /** Manages tracking and discouraging of misbehaving players. */
    public static NaughtyPlayerManager npmgr = new NaughtyPlayerManager();

    /** Contains information about the whole town. */
    public static TownObject townobj;

    // legacy static Presents services; try not to use these
    public static Invoker invoker;
    public static ConnectionManager conmgr;
    public static ClientManager clmgr;
    public static PresentsDObjectMgr omgr;
    public static InvocationManager invmgr;

    // legacy static Crowd services; try not to use these
    public static PlayerLocator locator;
    public static PlaceRegistry plreg;
    public static ChatProvider chatprov;
    public static LocationManager locman;

    /**
     * Ensures that the calling thread is the distributed object event dispatch thread, throwing an
     * {@link IllegalStateException} if it is not.
     */
    public static void requireDObjThread ()
    {
        if (!omgr.isDispatchThread()) {
            String errmsg = "This method must be called on the distributed object thread.";
            throw new IllegalStateException(errmsg);
        }
    }

    /**
     * Ensures that the calling thread <em>is not</em> the distributed object event dispatch
     * thread, throwing an {@link IllegalStateException} if it is.
     */
    public static void refuseDObjThread ()
    {
        if (omgr.isDispatchThread()) {
            String errmsg = "This method must not be called on the distributed object thread.";
            throw new IllegalStateException(errmsg);
        }
    }

    /**
     * The main entry point for the Bang server.
     */
    public static void main (String[] args)
    {
        // if we're on the dev server, up our long invoker warning to 3 seconds
        if (ServerConfig.config.getValue("auto_restart", false)) {
            Invoker.setDefaultLongThreshold(3000L);
        }

        Injector injector = Guice.createInjector(new Module());
        BangServer server = injector.getInstance(BangServer.class);
        try {
            server.init(injector);
            server.run();
            // annoyingly some background threads are hanging, so stick a fork in them for the time
            // being; when run() returns the dobj mgr and invoker thread will already have exited
            System.exit(0);
        } catch (Exception e) {
            log.warning("Server initialization failed.", e);
            System.exit(-1);
        }
    }

    @Override // documentation inherited
    public void init (final Injector injector)
        throws Exception
    {
        // create out database connection provider this must be done before calling super.init()
        conprov = _conprov;
        perCtx = _perCtx;

        // create our transition manager prior to doing anything else
        transitrepo = new TransitionRepository(conprov);

        // TEMP: the authenticator is going to access the player repository, so it needs to be
        // created here
        playrepo = new PlayerRepository(conprov);

        // set up some legacy static references
        invoker = _invoker;
        conmgr = _conmgr;
        clmgr = _clmgr;
        omgr = _omgr;
        invmgr = _invmgr;
        locator = _locator;
        plreg = _plreg;
        chatprov = _chatprov;
        locman = _locman;

        // do the base server initialization
        super.init(injector);

        // create and start up our auth invoker
        authInvoker = new Invoker("auth_invoker", omgr);
        authInvoker.setDaemon(true);
        authInvoker.start();

        // set up our authenticator
        author = (BangAuthenticator)_author;
        author.init();

        // configure the client manager to use the appropriate client class
        clmgr.setSessionFactory(new SessionFactory() {
            public Class<? extends PresentsSession> getSessionClass (AuthRequest areq) {
                return BangSession.class;
            }
            public Class<? extends ClientResolver> getClientResolverClass (Name username) {
                return BangClientResolver.class;
            }
        });

        // create our resource manager and other resource bits
        rsrcmgr = new ResourceManager("rsrc");
        rsrcmgr.initBundles(null, "config/resource/manager.properties", null);

        // create our avatar related bits
        comprepo = new BundledComponentRepository(rsrcmgr, null, AvatarCodes.AVATAR_RSRC_SET);
        alogic = new AvatarLogic(rsrcmgr, comprepo);

        // create our repositories
        itemrepo = new ItemRepository(conprov);
        gangrepo = new GangRepository(conprov);
        statrepo = new BangStatRepository(perCtx);
        ratingrepo = new RatingRepository(conprov);
        lookrepo = new LookRepository(conprov);
        bountyrepo = new BountyRepository(conprov);
        AccountActionRepository actionrepo = new AccountActionRepository(conprov);

        // create our various supporting managers
        playmgr = new PlayerManager();
        gangmgr = new GangManager();
        tournmgr = injector.getInstance(BangTourniesManager.class);
        ratingmgr = injector.getInstance(RatingManager.class);
        coinmgr = new BangCoinManager(conprov, actionrepo);
        coinexmgr = new BangCoinExchangeManager(conprov);
        actionmgr = new AccountActionManager(omgr, actionrepo);
        adminmgr = new BangAdminManager();

        // if we have a shared secret, assume we're running in a cluster
        String node = System.getProperty("node");
        if (node != null && ServerConfig.sharedSecret != null) {
            log.info("Running in cluster mode as node '" + ServerConfig.nodename + "'.");
            peermgr = injector.getInstance(BangPeerManager.class);
        }

        // create and set up our configuration registry and admin service
        confreg = new DatabaseConfigRegistry(perCtx, invoker, ServerConfig.nodename);
        AdminProvider.init(invmgr, confreg);

        // now initialize our runtime configuration, postponing the remaining server initialization
        // until our configuration objects are available
        RuntimeConfig.init(omgr);
        omgr.postRunnable(new PresentsDObjectMgr.LongRunnable () {
            public void run () {
                try {
                    finishInit(injector);
                } catch (Exception e) {
                    log.warning("Server initialization failed.", e);
                    System.exit(-1);
                }
            }
        });

        // start up an interval that checks to see if our code has changed and auto-restarts the
        // server as soon as possible when it has
        if (ServerConfig.config.getValue("auto_restart", false)) {
            _codeModified = new File(ServerConfig.serverRoot, "dist/bang-code.jar").lastModified();
            new Interval(omgr) {
                public void expired () {
                    checkAutoRestart();
                }
            }.schedule(AUTO_RESTART_CHECK_INTERVAL, true);
        }
    }

    /**
     * This is called once our runtime configuration is available.
     */
    protected void finishInit (Injector injector)
        throws Exception
    {
        // initialize our managers
        boardmgr.init(conprov);
        playmgr.init(conprov);
        gangmgr.init(conprov);
        tournmgr.init(injector);
        ratingmgr.init(conprov);
        coinexmgr.init();
        adminmgr.init(_shutmgr, omgr);
        if (peermgr != null) {
            peermgr.init(injector, ServerConfig.nodename, ServerConfig.sharedSecret,
                         ServerConfig.hostname, ServerConfig.publicHostname, getListenPorts()[0]);
        }

        // create our managers
        saloonmgr = (SaloonManager)plreg.createPlace(new SaloonConfig());
        storemgr = (StoreManager)plreg.createPlace(new StoreConfig());
        bankmgr = (BankManager)plreg.createPlace(new BankConfig());
        ranchmgr = (RanchManager)plreg.createPlace(new RanchConfig());
        barbermgr = (BarberManager)plreg.createPlace(new BarberConfig());
        stationmgr = (StationManager)plreg.createPlace(new StationConfig());
        hideoutmgr = (HideoutManager)plreg.createPlace(new HideoutConfig());
        officemgr = (OfficeManager)plreg.createPlace(new OfficeConfig());

        // create the town object and initialize the locator which will keep it up-to-date
        townobj = omgr.registerObject(new TownObject());
        _locator.init();

        log.info("Bang server v" + DeploymentConfig.getVersion() + " initialized.");
    }

    /**
     * Loads a message to the general audit log.
     */
    public static void generalLog (String message)
    {
        _glog.log(message);
    }

    /**
     * Loads a message to the item audit log.
     */
    public static void itemLog (String message)
    {
        _ilog.log(message);
    }

    /**
     * Loads a message to the client performance log.
     */
    public static void perfLog (String message)
    {
        _plog.log(message);
    }

    /**
     * Creates an audit log with the specified name (which should includ the <code>.log</code>
     * suffix) in our server log directory.
     */
    public static AuditLogger createAuditLog (String logname)
    {
        // qualify our log file with the nodename to avoid collisions
        logname = logname + "_" + ServerConfig.nodename;
        return new AuditLogger(_logdir, logname + ".log");
    }

    @Override // documentation inherited
    protected int[] getListenPorts ()
    {
        return DeploymentConfig.getServerPorts(ServerConfig.townId);
    }

    @Override // from PresentsServer
    protected void invokerDidShutdown ()
    {
        super.invokerDidShutdown();

        // shutdown our persistence context
        perCtx.shutdown();

        // close our audit logs
        _glog.close();
        _ilog.close();
        _stlog.close();
        _plog.close();
        BangCoinManager.coinlog.close();
    }

    protected void checkAutoRestart ()
    {
        long lastModified = new File(ServerConfig.serverRoot, "dist/bang-code.jar").lastModified();
        if (lastModified > _codeModified) {
            int players = 0;
            for (Iterator<ClientObject> iter = clmgr.enumerateClientObjects(); iter.hasNext(); ) {
                if (iter.next() instanceof PlayerObject) {
                    players++;
                }
            }
            if (players == 0) {
                adminmgr.scheduleReboot(0, "codeUpdateAutoRestart");
            }
        }
    }

    /** Used to direct our server reports to an audit log file. */
    protected static class BangReportManager extends ReportManager
    {
        @Override protected void logReport (String report) {
            _stlog.log(report);
        }
    }

    protected long _codeModified;

    @Inject protected ConnectionProvider _conprov;
    @Inject protected PersistenceContext _perCtx;
    @Inject protected Authenticator _author;
    @Inject protected PlayerLocator _locator;
    @Inject protected ParlorManager _parmgr;

    protected static File _logdir = new File(ServerConfig.serverRoot, "log");
    protected static AuditLogger _glog = createAuditLog("server");
    protected static AuditLogger _ilog = createAuditLog("item");
    protected static AuditLogger _stlog = createAuditLog("state");
    protected static AuditLogger _plog = createAuditLog("perf");

    /** Check for modified code every 30 seconds. */
    protected static final long AUTO_RESTART_CHECK_INTERVAL = 30 * 1000L;
}
