//
// $Id$

package com.threerings.bang.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.ArrayUtil;
import com.samskivert.util.CollectionUtil;
import com.samskivert.util.ResultListener;
import com.samskivert.util.StringUtil;

import com.threerings.crowd.server.CrowdClientResolver;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.dobj.DSet;

import com.threerings.stats.data.Stat;
import com.threerings.stats.data.StatSet;

import com.threerings.bang.gang.data.GangObject;
import com.threerings.bang.gang.server.persist.GangInviteRecord;
import com.threerings.bang.gang.server.persist.GangMemberRecord;

import com.threerings.bang.admin.server.RuntimeConfig;
import com.threerings.bang.avatar.data.Look;
import com.threerings.bang.avatar.util.AvatarLogic;

import com.threerings.bang.data.Article;
import com.threerings.bang.data.BangCodes;
import com.threerings.bang.data.BangCredentials;
import com.threerings.bang.data.BangTokenRing;
import com.threerings.bang.data.FreeTicket;
import com.threerings.bang.data.GuestHandle;
import com.threerings.bang.data.Handle;
import com.threerings.bang.data.Item;
import com.threerings.bang.data.Notification;
import com.threerings.bang.data.PlayerObject;
import com.threerings.bang.data.Rating;
import com.threerings.bang.data.StatType;
import com.threerings.bang.data.TrainTicket;
import com.threerings.bang.util.BangUtil;

import com.threerings.bang.server.persist.FolkRecord;
import com.threerings.bang.server.persist.PardnerRecord;
import com.threerings.bang.server.persist.PlayerRecord;

import static com.threerings.bang.Log.log;

/**
 * Customizes the client resolver to use our {@link PlayerObject}.
 */
public class BangClientResolver extends CrowdClientResolver
{
    /**
     * This is called earlier in the authentication process where we have to load an account's
     * player record, so we stash it here to avoid loading it again when the time comes to resolve
     * their data.
     */
    public static void stashPlayer (PlayerRecord player)
    {
        synchronized (_pstash) {
            _pstash.put(player.accountName.toLowerCase(), player);
        }
    }

    /**
     * This is called earlier in the authentication process where we find out the user's age
     * so we stash it here to avoid loading it again when the time comes to resolve their data.
     */
    public static void stashPlayerOver13 (String accountName)
    {
        synchronized (_astash) {
            _astash.add(accountName.toLowerCase());
        }
    }

    // documentation inherited
    public ClientObject createClientObject ()
    {
        return new PlayerObject();
    }

    // documentation inherited
    protected void resolveClientData (ClientObject clobj)
        throws Exception
    {
        super.resolveClientData(clobj);
        PlayerObject buser = (PlayerObject)clobj;
        String username = buser.username.toString();
        PlayerRecord player;

        // check for a stashed player record
        synchronized (_pstash) {
            player = _pstash.remove(username.toLowerCase());
        }

        // if we have nothing in the stash, we need to load them from the db
        if (player == null) {
            player = BangServer.playrepo.loadPlayer(username);
        }

        // if they're not in the db, it's their first time, how nice
        if (player == null) {
            BangClient client = (BangClient)BangServer.clmgr.getClient(buser.username);
            boolean anonymous = ((BangCredentials)client.getCredentials()).anonymous;
            player = new PlayerRecord(username, anonymous);
            BangServer.playrepo.insertPlayer(player);
            if (!anonymous) {
                BangServer.author.setAccountIsActive(username, true);
                synchronized (_astash) {
                    if (_astash.remove(username.toLowerCase())) {
                        player.isOver13 = true;
                    }
                }
            }
            BangServer.generalLog(
                    "first_timer " + player.playerId + (anonymous ? " anon" : " account"));
        }

        buser.playerId = player.playerId;
        if (player.handle != null) {
            buser.handle = new Handle(player.handle);
        } else {
            buser.handle = new GuestHandle("!!" + username);
        }
        buser.isMale = player.isSet(PlayerRecord.IS_MALE_FLAG);
        buser.tokens.setToken(BangTokenRing.ANONYMOUS, player.isSet(PlayerRecord.IS_ANONYMOUS));
        buser.tokens.setToken(BangTokenRing.OVER_13, player.isOver13);
        buser.tokens.setToken(BangTokenRing.DEMO, player.isSet(PlayerRecord.IS_DEMO_ACCOUNT));
        buser.scrip = player.scrip;
        buser.coins = BangServer.coinmgr.getCoinRepository().getCoinCount(player.accountName);

        // load up this player's items
        ArrayList<Item> items = BangServer.itemrepo.loadItems(buser.playerId);
        long now = System.currentTimeMillis();
        // check for expired items
        ArrayIntSet removals = new ArrayIntSet();
        for (Iterator<Item> iter = items.iterator(); iter.hasNext(); ) {
            Item item = iter.next();
            if (item instanceof Article && ((Article)item).isExpired(now)) {
                removals.add(item.getItemId());
                iter.remove();
                BangServer.itemrepo.deleteItem(item, "Article expired");
            }
        }
        buser.inventory = new DSet<Item>(items.iterator());

        // load up this player's persistent stats
        ArrayList<Stat> stats = BangServer.statrepo.loadStats(buser.playerId);
        buser.stats = new StatSet(stats.iterator());
        buser.stats.setContainer(buser);

        // if this player started playing before the end of beta and they've played 20 ranked
        // games, give them a free permanent ticket to ITP
        int itpidx = BangUtil.getTownIndex(BangCodes.INDIAN_POST);
        boolean holdsITPTicket = buser.holdsTicket(BangCodes.INDIAN_POST);
        if (buser.playerId < BangCodes.BETA_PLAYER_CUTOFF && !holdsITPTicket &&
            buser.stats.getIntStat(StatType.GAMES_PLAYED) >= FREE_ITP_GP_REQUIREMENT) {
            log.info("Granting free ITP ticket to beta player [who=" + username +
                     ", handle=" + buser.handle + ", pid=" + buser.playerId +
                     ", games=" + buser.stats.getIntStat(StatType.GAMES_PLAYED) + "].");
            TrainTicket ticket = new TrainTicket(buser.playerId, itpidx);
            BangServer.itemrepo.insertItem(ticket);
            BangServer.playrepo.grantTownAccess(buser.playerId, ticket.getTownId());
            buser.addToInventory(ticket);
            holdsITPTicket = true;

        // fix bug with ticket granting
        } else if (holdsITPTicket &&
                ((player.townId == null) || player.townId.equals(BangCodes.FRONTIER_TOWN))) {
            BangServer.playrepo.grantTownAccess(buser.playerId, BangCodes.INDIAN_POST);
        }

        // if they have an expired free ticket, remove it
        boolean noFreeTicket = true;
        for (Item item : items) {
            if (!(item instanceof FreeTicket)) {
                continue;
            }
            noFreeTicket = false;
            FreeTicket ticket = (FreeTicket)item;
            if (ticket.isExpired(System.currentTimeMillis()) ||
                    buser.holdsTicket(ticket.getTownId())) {
                BangServer.itemrepo.deleteItem(item, "Free Ticket Expired");
                buser.removeFromInventory(item.getKey());
            }
        }

        // Give out a free ticket if a player qualified but never successfully made it to ITP
        if (noFreeTicket && player.nextTown == null &&
                buser.stats.containsValue(StatType.FREE_TICKETS, BangCodes.INDIAN_POST) &&
                !buser.stats.containsValue(StatType.ACTIVATED_TICKETS, BangCodes.INDIAN_POST)) {
            FreeTicket ticket = FreeTicket.checkQualifies(
                    buser, BangUtil.getTownIndex(BangCodes.INDIAN_POST));
            if (ticket != null) {
                BangServer.itemrepo.insertItem(ticket);
                buser.addToInventory(ticket);
            }
        }

        // if we're giving out free access to ITP, give the user a temporary ITP ticket for this
        // session (if they don't already have one)
        if (RuntimeConfig.server.freeIndianPost && !buser.tokens.isAnonymous() && !holdsITPTicket &&
                !buser.holdsFreeTicket(BangCodes.INDIAN_POST)) {
            buser.addToInventory(new TrainTicket(buser.playerId, itpidx));
        }

        // load up this player's ratings
        buser.ratings = BangServer.ratingrepo.loadRatings(buser.playerId);

        // load up this player's avatar looks and modify any looks that have now expired articles
        ArrayList<Look> looks = BangServer.lookrepo.loadLooks(player.playerId);
        if (!removals.isEmpty()) {
            ArrayList<Look> modified = AvatarLogic.stripLooks(
                    removals, buser.inventory, looks);
            for (Look look : modified) {
                BangServer.lookrepo.updateLook(buser.playerId, look);
            }
        }
        buser.looks = new DSet<Look>(looks);

        // configure their chosen poses
        buser.poses = new String[Look.POSE_COUNT];
        buser.poses[Look.Pose.DEFAULT.ordinal()] = player.look;
        buser.poses[Look.Pose.VICTORY.ordinal()] = player.victoryLook;
        buser.poses[Look.Pose.WANTED_POSTER.ordinal()] = player.wantedLook;

        // initialize the set of notifications
        buser.notifications = new DSet<Notification>();

        // load up this player's pardners
        _precords = BangServer.playmgr.getPardnerRepository().getPardnerRecords(player.playerId);

        // load up this player's gang information
        _grecord = BangServer.gangrepo.loadMember(player.playerId);
        if (_grecord == null) {
            _ginvites = BangServer.gangrepo.getInviteRecords(player.playerId);
        }

        // load this player's friends and foes
        ArrayList<FolkRecord> folks = BangServer.playrepo.loadOpinions(buser.playerId);
        ArrayIntSet friends = new ArrayIntSet(), foes = new ArrayIntSet();
        for (FolkRecord folk : folks) {
            (folk.opinion == FolkRecord.FRIEND ? friends : foes).add(folk.targetId);
        }
        // toIntArray() returns a sorted array
        buser.friends = friends.toIntArray();
        buser.foes = foes.toIntArray();

        // make a note of this player's redeemable rewards
        _rewards = player.rewards;
    }

    @Override // documentation inherited
    public void handleResult ()
    {
        // if something went wrong or the player isn't in a gang, we can go right to the final
        // processing stage
        if (_grecord == null || _failure != null) {
            super.handleResult();
            return;
        }
        // otherwise, we must resolve the gang through the gang manager
        BangServer.gangmgr.resolveGang(_grecord.gangId,
            new ResultListener<GangObject>() {
                public void requestCompleted (GangObject result) {
                    BangClientResolver.super.handleResult();
                }
                public void requestFailed (Exception cause) {
                    // let them log in as if they didn't belong to a gang
                    _grecord = null;
                    BangClientResolver.super.handleResult();
                }
            });
    }

    @Override // documentation inherited
    protected void finishResolution (ClientObject clobj)
    {
        super.finishResolution(clobj);
        PlayerObject buser = (PlayerObject)clobj;

        // initialize our pardner information
        BangServer.playmgr.initPardners(buser, _precords);

        // initialize our gang information
        BangServer.gangmgr.initPlayer(buser, _grecord, _ginvites);

        // redeem any rewards for which this player is eligible
        if (_rewards != null && _rewards.size() > 0) {
            BangServer.playmgr.redeemRewards(buser, _rewards);
        }
    }

    /** A temporary handle on this player's pardner records. */
    protected ArrayList<PardnerRecord> _precords;

    /** A temporary handle on this player's gang membership information (or null). */
    protected GangMemberRecord _grecord;

    /** A temporary list of this player's gang invitations (or null). */
    protected ArrayList<GangInviteRecord> _ginvites;

    /** Activated rewards that were redeemed for this player during authentication. */
    protected ArrayList<String> _rewards;

    /** Used to temporarily store player records during resolution. */
    protected static HashMap<String,PlayerRecord> _pstash = new HashMap<String,PlayerRecord>();

    /** Used to temporarily store player age during resolution. */
    protected static HashSet<String> _astash = new HashSet<String>();

    /** The number of rated games a player has to have played to get a free ticket to ITP. */
    protected static final int FREE_ITP_GP_REQUIREMENT = 20;
}
