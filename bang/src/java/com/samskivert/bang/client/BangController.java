//
// $Id$

package com.samskivert.bang.client;

import java.awt.event.ActionEvent;

import com.samskivert.swing.event.CommandEvent;
import com.samskivert.util.StringUtil;

import com.threerings.presents.dobj.MessageEvent;
import com.threerings.presents.dobj.MessageListener;

import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.PlaceConfig;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.crowd.util.CrowdContext;

import com.threerings.parlor.game.client.GameController;

import com.threerings.toybox.util.ToyBoxContext;

import com.samskivert.bang.data.BangObject;
import com.samskivert.bang.data.effect.Effect;

import static com.samskivert.bang.Log.log;

/**
 * Handles the logic and flow of the client side of a game.
 */
public class BangController extends GameController
{
    /** The name of the command posted by the "Back to lobby" button in
     * the side bar. */
    public static final String BACK_TO_LOBBY = "BackToLobby";

    /** A command that requests to move a piece. */
    public static final String MOVE_AND_FIRE = "MoveAndFire";

    @Override // documentation inherited
    public void init (CrowdContext ctx, PlaceConfig config)
    {
        super.init(ctx, config);
        _ctx = (ToyBoxContext)ctx;
    }

    @Override // documentation inherited
    public void willEnterPlace (PlaceObject plobj)
    {
        super.willEnterPlace(plobj);
        _bangobj = (BangObject)plobj;

        // determine our player index
        BodyObject me = (BodyObject)_ctx.getClient().getClientObject();
        _pidx = _bangobj.getPlayerIndex(me.username);

        // we may be returning to an already started game
        if (_bangobj.isInPlay()) {
            _panel.startGame(_bangobj, _pidx);
        }
    }

    /** Handles a request to leave the game. Generated by the {@link
     * #BACK_TO_LOBBY} command. */
    public void handleBackToLobby (Object source)
    {
        _ctx.getLocationDirector().moveBack();
    }

    /** Handles a request to move a piece. Generated by the
     * {@link #MOVE_AND_FIRE} command. */
    public void handleMoveAndFire (Object source, int[] data)
    {
        log.info("Requesting move and fire: " + StringUtil.toString(data));
        BangService.InvocationListener il =
            new BangService.InvocationListener() {
            public void requestFailed (String reason) {
                // TODO: play a sound or highlight the piece that failed
                // to move
                log.info("Thwarted! " + reason);
            }
        };
        _bangobj.service.move(_ctx.getClient(), data[0], (short)data[1],
                              (short)data[2], data[3], il);
    }

    @Override // documentation inherited
    protected PlaceView createPlaceView (CrowdContext ctx)
    {
        _panel = new BangPanel((ToyBoxContext)ctx, this);
        return _panel;
    }

    @Override // documentation inherited
    protected void gameDidStart ()
    {
        super.gameDidStart();

        // we may be returning to an already started game
        _panel.startGame(_bangobj, _pidx);
    }

    @Override // documentation inherited
    protected void gameWillReset ()
    {
        super.gameWillReset();
        _panel.endGame();
    }

    @Override // documentation inherited
    protected void gameDidEnd ()
    {
        super.gameDidEnd();
        _panel.endGame();
    }

    /** A casted reference to our context. */
    protected ToyBoxContext _ctx;

    /** Contains our main user interface. */
    protected BangPanel _panel;

    /** A casted reference to our game object. */
    protected BangObject _bangobj;

    /** Our player index or -1 if we're not a player. */
    protected int _pidx;
}
