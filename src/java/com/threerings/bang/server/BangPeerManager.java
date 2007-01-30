//
// $Id$

package com.threerings.bang.server;

import com.samskivert.io.PersistenceException;
import com.samskivert.jdbc.ConnectionProvider;
import com.samskivert.util.HashIntMap;
import com.samskivert.util.Invoker;
import com.samskivert.util.ObserverList;
import com.samskivert.util.Tuple;

import com.threerings.presents.data.ClientObject;
import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.SetListener;

import com.threerings.presents.peer.data.ClientInfo;
import com.threerings.presents.peer.data.NodeObject;
import com.threerings.presents.peer.server.persist.NodeRecord;
import com.threerings.presents.server.PresentsClient;

import com.threerings.crowd.peer.server.CrowdPeerManager;

import com.threerings.bang.avatar.data.Look;
import com.threerings.bang.data.BangClientInfo;
import com.threerings.bang.data.BangNodeObject;
import com.threerings.bang.data.BangPeerMarshaller;
import com.threerings.bang.data.Handle;
import com.threerings.bang.data.Item;
import com.threerings.bang.data.PlayerObject;
import com.threerings.bang.util.BangUtil;

import static com.threerings.bang.Log.log;

/**
 * Extends the standard peer services and handles some Bang specific business
 * like pardner presence reporting.
 */
public class BangPeerManager extends CrowdPeerManager
    implements BangPeerProvider
{
    /**
     * Used by entities that wish to know when a player logs onto or off of this or one of our peer
     * servers.
     */
    public static interface RemotePlayerObserver
    {
        /**
         * Called when a player logs on to this or one of our peer servers.
         */
        public void remotePlayerLoggedOn (int townIndex, BangClientInfo info);

        /**
         * Called when a player logs off of this or one of our peer servers.
         */
        public void remotePlayerLoggedOff (int townIndex, BangClientInfo info);
    }

    public BangPeerManager (ConnectionProvider conprov, Invoker invoker)
        throws PersistenceException
    {
        super(conprov, invoker);
    }

    /**
     * Registers a remote player observer.
     */
    public void addPlayerObserver (RemotePlayerObserver observer)
    {
        _remobs.add(observer);
    }

    /**
     * Removes a remote player observer registration.
     */
    public void removePlayerObserver (RemotePlayerObserver observer)
    {
        _remobs.remove(observer);
    }

    /**
     * Returns information on the specified player if they are currently logged onto one of our
     * peer servers.
     *
     * @return the player's client info and the town index of the node to which they are connected
     * or null if the player is connected to no peer node.
     */
    public Tuple<BangClientInfo,Integer> locateRemotePlayer (Handle handle)
    {
        for (PeerNode peer : _peers.values()) {
            if (peer.nodeobj == null) { // skip uninitialized peers
                continue;
            }
            BangClientInfo info = (BangClientInfo)peer.nodeobj.clients.get(handle);
            if (info != null) {
                return new Tuple<BangClientInfo,Integer>(info, ((BangPeerNode)peer).townIndex);
            }
        }
        return null;
    }
    
    /**
     * Requests to deliver the specified item to its owner if he's logged into one of our peer
     * servers.
     *
     * @param source a qualified translatable string describing the source of the item
     * @return true if the item was successfully delivered, false if the user was not online.
     */
    public boolean forwardItem (Item item, String source)
    {
        for (PeerNode peer : _peers.values()) {
            if (peer.nodeobj == null) {
                continue;
            }
            if (((BangPeerNode)peer).players.containsKey(item.getOwnerId())) {
                ((BangNodeObject)peer.nodeobj).bangPeerService.deliverItem(
                    peer.getClient(), item, source);
                return true;
            }
        }
        return false;
    }

    // from interface BangPeerProvider
    public void deliverItem (ClientObject caller, Item item, String source)
    {
        PlayerObject user = BangServer.lookupPlayer(item.getOwnerId());
        if (user != null) {
            BangServer.playmgr.deliverItemLocal(user, item, source);
        }
    }
    
    @Override // from CrowdPeerManager
    public void shutdown ()
    {
        super.shutdown();
        
        // clear out our invocation service
        if (_nodeobj != null) {
            BangServer.invmgr.clearDispatcher(((BangNodeObject)_nodeobj).bangPeerService);
        }
    }
    
    @Override // from PeerManager
    protected PeerNode createPeerNode (NodeRecord record)
    {
        return new BangPeerNode(record);
    }

    @Override // from CrowdPeerManager
    protected NodeObject createNodeObject ()
    {
        return new BangNodeObject();
    }

    @Override // from CrowdPeerManager
    protected ClientInfo createClientInfo ()
    {
        return new BangClientInfo();
    }

    @Override // from CrowdPeerManager
    protected void initClientInfo (PresentsClient client, ClientInfo info)
    {
        super.initClientInfo(client, info);
        BangClientInfo binfo = (BangClientInfo)info;
        PlayerObject player = (PlayerObject)client.getClientObject();
        binfo.playerId = player.playerId;
        
        // grab a snapshot of this player's avatar which is how they'll look to
        // pardners on other servers
        Look look = player.getLook(Look.Pose.DEFAULT);
        binfo.avatar = (look == null) ? null : look.getAvatar(player);
    }

    @Override // from CrowdPeerManager
    protected void didInit ()
    {
        super.didInit();

        // stuff our town information into our node object
        BangNodeObject bnodeobj = (BangNodeObject)_nodeobj;
        bnodeobj.setTownId(ServerConfig.townId);
        bnodeobj.setBangPeerService(
            (BangPeerMarshaller)BangServer.invmgr.registerDispatcher(
                new BangPeerDispatcher(this), false));
    }

    /**
     * Called when a player logs onto one of our peer servers.
     */
    protected void remotePlayerLoggedOn (final int townIndex, final BangClientInfo info)
    {
        // notify our remote player observers
        _remobs.apply(new ObserverList.ObserverOp<RemotePlayerObserver>() {
            public boolean apply (RemotePlayerObserver observer) {
                observer.remotePlayerLoggedOn(townIndex, info);
                return true;
            }
        });
    }

    /**
     * Called when a player logs off of one of our peer servers.
     */
    protected void remotePlayerLoggedOff (final int townIndex, final BangClientInfo info)
    {
        // notify our remote player observers
        _remobs.apply(new ObserverList.ObserverOp<RemotePlayerObserver>() {
            public boolean apply (RemotePlayerObserver observer) {
                observer.remotePlayerLoggedOff(townIndex, info);
                return true;
            }
        });
    }

    protected class BangPeerNode extends PeerNode
        implements SetListener
    {
        protected int townIndex;
        protected HashIntMap<BangClientInfo> players = new HashIntMap<BangClientInfo>();

        public BangPeerNode (NodeRecord record) {
            super(record);
        }

        @Override // from PeerNode
        public void objectAvailable (NodeObject object) {
            super.objectAvailable(object);

            // add ourselves as a listener to hear future DSet events
            object.addListener(this);

            // look up this node's town index once and store it
            townIndex = BangUtil.getTownIndex(((BangNodeObject)object).townId);
            log.info("Got peer object " + townIndex);

            // map and issue a remotePlayerLoggedOn for all logged on players
            for (ClientInfo info : object.clients) {
                BangClientInfo binfo = (BangClientInfo)info;
                players.put(binfo.playerId, binfo);
                remotePlayerLoggedOn(townIndex, binfo);
            }
        }

        public void entryAdded (EntryAddedEvent event) {
            log.info("Remote entry added " + event);
            if (event.getName().equals(NodeObject.CLIENTS)) {
                BangClientInfo info = (BangClientInfo)event.getEntry();
                players.put(info.playerId, info);
                remotePlayerLoggedOn(townIndex, info);
            }
        }

        public void entryUpdated (EntryUpdatedEvent event) {
        }

        public void entryRemoved (EntryRemovedEvent event) {
            log.info("Remote entry removed " + event);
            if (event.getName().equals(NodeObject.CLIENTS)) {
                BangClientInfo info = (BangClientInfo)event.getOldEntry();
                players.remove(info.playerId);
                remotePlayerLoggedOff(townIndex, info);
            }
        }
    }

    protected ObserverList<RemotePlayerObserver> _remobs = new ObserverList<RemotePlayerObserver>(
        ObserverList.FAST_UNSAFE_NOTIFY);
}
