//
// $Id$

package com.threerings.bang.saloon.client;

import com.jmex.bui.BButton;
import com.jmex.bui.BContainer;
import com.jmex.bui.BLabel;
import com.jmex.bui.event.ActionEvent;
import com.jmex.bui.event.ActionListener;
import com.jmex.bui.icon.ImageIcon;
import com.jmex.bui.layout.GroupLayout;
import com.jmex.bui.util.Point;
import com.jmex.bui.util.Rectangle;

import com.samskivert.util.StringUtil;

import com.threerings.crowd.data.OccupantInfo;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.SetAdapter;
import com.threerings.util.MessageBundle;
import com.threerings.util.Name;

import com.threerings.bang.chat.client.PlaceChatView;
import com.threerings.bang.client.ShopView;
import com.threerings.bang.client.TownButton;
import com.threerings.bang.client.WalletLabel;
import com.threerings.bang.client.bui.StatusLabel;
import com.threerings.bang.data.BangBootstrapData;
import com.threerings.bang.util.BangContext;

import com.threerings.bang.saloon.data.ParlorObject;
import com.threerings.bang.saloon.data.SaloonCodes;

/**
 * Displays the main Parlor interface wherein a player can meet up with other
 * players to play games and inspect the various top-N lists.
 */
public class ParlorView extends ShopView
    implements ActionListener
{
    public ParlorView (BangContext ctx, ParlorController ctrl)
    {
        super(ctx, SaloonCodes.SALOON_MSGS);
        _ctrl = ctrl;

        // add our various interface components
        add(new BLabel(_msgs.get("m.title"), "shop_status"),
            new Rectangle(266, 656, 491, 33));
        add(new WalletLabel(ctx, true), new Rectangle(25, 40, 150, 40));
        add(createHelpButton(), new Point(800, 25));
        add(new BButton(_msgs.get("m.to_saloon"), this, "to_saloon"),
            new Point(870, 25));

        add(_config = new ParlorConfigIcons(_ctx), new Point(450, 263));
        add(_chat = new PlaceChatView(ctx, _msgs.get("m.parlor_chat")),
            new Rectangle(552, 78, 445, 551));


        add(_status = new StatusLabel(ctx), new Rectangle(276, 8, 500, 54));
        _status.setStyleClass("shop_status");
        _status.setText(_msgs.get("m.intro_tip"));

        // display some images over the ones baked into the background
        ImageIcon banner = new ImageIcon(
            _ctx.loadImage("ui/saloon/play_parlor_game.png"));
        add(new BLabel(banner), new Point(206, 578));
        banner = new ImageIcon(_ctx.loadImage("ui/saloon/parlor_settings.png"));
        add(new BLabel(banner), new Point(90, 260));

        // create our config view, but we'll add it later
        _gconfig = new ParlorGameConfigView(_ctx, _status);

        // allow the parlor owner to change the settings
        add(_settings = new BButton(_msgs.get("m.settings"), this, "settings"), new Point(340, 81));
        _settings.setVisible(false);
    }

    /**
     * Called by the controller to instruct us to display the pending match
     * view when we have requested to play a game.
     */
    public void displayMatchView ()
    {
        // remove our configuration view
        if (_gconfig.isAdded()) {
            remove(_gconfig);
        }

        // this should never happen, but just to be ultra-robust
        if (_mview != null) {
            remove(_mview);
            _mview = null;
        }

        // display a match view for this pending match
        add(_mview = new ParlorMatchView(_ctx, _parobj), SaloonView.CRIT_RECT);
    }

    /**
     * Called by the match view if the player cancels their pending match.
     * Redisplays the criterion view.
     */
    public void clearMatchView ()
    {
        // out with the old match view
        if (_mview != null) {
            remove(_mview);
            _mview = null;
        }

        // redisplay the criterion view
        if (!_gconfig.isAdded()) {
            add(_gconfig, SaloonView.CRIT_RECT);
        }
    }

    public void setStatus (String status)
    {
        String msg = StringUtil.isBlank(status) ? "" : _msgs.xlate(status);
        _status.setStatus(msg, false);
    }

    // documentation inherited from interface ActionListener
    public void actionPerformed (ActionEvent event)
    {
        if ("to_saloon".equals(event.getAction())) {
            BangBootstrapData bbd = (BangBootstrapData)
                _ctx.getClient().getBootstrapData();
            _ctx.getLocationDirector().moveTo(bbd.saloonOid);

        } else if ("settings".equals(event.getAction())) {
            _ctx.getBangClient().displayPopup(new ParlorConfigView(_ctx, _parobj), true);
        }
    }

    @Override // documentation inherited
    public void willEnterPlace (PlaceObject plobj)
    {
        _parobj = (ParlorObject)plobj;

        _gconfig.willEnterPlace(_parobj);
        _config.willEnterPlace(_parobj);

        // show the match view if there's a game already in progress
        if (_parobj.playerOids != null) {
            displayMatchView();
        } else {
            clearMatchView();
        }

        add(new BLabel(_msgs.get("m.parlor_name", _parobj.info.creator), "parlor_label"),
                new Point(165, 263));
        add(new FolkView(_ctx, plobj, false), new Rectangle(95, 119, 407, 130));
        if (_ctx.getUserObject().handle.equals(_parobj.info.creator)) {
            _settings.setVisible(true);
        }
    }

    @Override // documentation inherited
    public void didLeavePlace (PlaceObject plobj)
    {
        _gconfig.didLeavePlace();
        _config.didLeavePlace();

        // unregister our chat display
        _chat.shutdown();

        if (_parobj != null) {
            _parobj = null;
        }
    }

    @Override // documentation inherited
    protected Point getShopkeepNameLocation ()
    {
        return new Point(22, 554);
    }

    protected ParlorObject _parobj;
    protected ParlorController _ctrl;
    protected StatusLabel _status;
    protected PlaceChatView _chat;
    protected BContainer _ccont;
    protected BButton _settings;

    protected ParlorGameConfigView _gconfig;
    protected ParlorConfigIcons _config;
    protected ParlorMatchView _mview;
}
