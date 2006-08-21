//
// $Id$

package com.threerings.bang.server;

import java.util.ArrayList;
import java.util.HashMap;

import com.samskivert.util.RandomUtil;

import com.threerings.crowd.server.CrowdClientResolver;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.dobj.DSet;

import com.threerings.bang.avatar.data.Look;
import com.threerings.bang.data.Handle;
import com.threerings.bang.data.Item;
import com.threerings.bang.data.PlayerObject;
import com.threerings.bang.data.Rating;
import com.threerings.bang.data.Stat;
import com.threerings.bang.data.StatSet;
import com.threerings.bang.server.persist.PlayerRecord;

/**
 * Customizes the client resolver to use our {@link PlayerObject}.
 */
public class BangClientResolver extends CrowdClientResolver
{
    /**
     * This is called earlier in the authentication process where we have to
     * load an account's player record, so we stash it here to avoid loading it
     * again when the time comes to resolve their data.
     */
    public static void stashPlayer (PlayerRecord player)
    {
        synchronized (_pstash) {
            _pstash.put(player.accountName.toLowerCase(), player);
        }
    }

    // documentation inherited
    public Class<? extends ClientObject> getClientObjectClass ()
    {
        return PlayerObject.class;
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
            // it's their first time, how nice
            player = new PlayerRecord(username);
            BangServer.playrepo.insertPlayer(player);
            BangServer.generalLog("first_timer " + username);
        }

        buser.playerId = player.playerId;
        if (player.handle != null) {
            buser.handle = new Handle(player.handle);
        }
        buser.isMale = player.isSet(PlayerRecord.IS_MALE_FLAG);
        buser.scrip = player.scrip;
        buser.coins = BangServer.coinmgr.getCoinRepository().getCoinCount(
            player.accountName);

        // load up this player's items
        ArrayList<Item> items = BangServer.itemrepo.loadItems(buser.playerId);
        buser.inventory = new DSet<Item>(items.iterator());

        // load up this player's persistent stats
        ArrayList<Stat> stats = BangServer.statrepo.loadStats(buser.playerId);
        buser.stats = new StatSet(stats.iterator());
        buser.stats.setContainer(buser);

        // load up this player's ratings
        ArrayList<Rating> ratings =
            BangServer.ratingrepo.loadRatings(buser.playerId);
        buser.ratings = new DSet<Rating>(ratings.iterator());

        // load up this player's avatar looks
        buser.looks = new DSet<Look>(
            BangServer.lookrepo.loadLooks(player.playerId).iterator());

        // configure their chosen poses
        buser.poses = new String[Look.POSE_COUNT];
        buser.poses[Look.Pose.DEFAULT.ordinal()] = player.look;
        buser.poses[Look.Pose.VICTORY.ordinal()] = player.victoryLook;
        buser.poses[Look.Pose.WANTED_POSTER.ordinal()] = player.wantedLook;
        
        // load up this player's pardners
        BangServer.playmgr.loadPardners(buser);
    }

    protected static HashMap<String,PlayerRecord> _pstash =
        new HashMap<String,PlayerRecord>();
}
