//
// $Id$

package com.threerings.bang.store.client;

import java.awt.image.BufferedImage;

import com.jmex.bui.BButton;
import com.jmex.bui.BComponent;
import com.jmex.bui.BContainer;
import com.jmex.bui.BDecoratedWindow;
import com.jmex.bui.BImage;
import com.jmex.bui.BLabel;
import com.jmex.bui.BTextArea;
import com.jmex.bui.Spacer;
import com.jmex.bui.event.ActionEvent;
import com.jmex.bui.event.ActionListener;
import com.jmex.bui.icon.BlankIcon;
import com.jmex.bui.icon.ImageIcon;
import com.jmex.bui.layout.AbsoluteLayout;
import com.jmex.bui.layout.GroupLayout;
import com.jmex.bui.util.Point;
import com.jmex.bui.util.Rectangle;

import com.threerings.media.image.Colorization;
import com.threerings.media.image.ImageUtil;

import com.threerings.util.MessageBundle;

import com.threerings.bang.client.BangUI;
import com.threerings.bang.client.MoneyLabel;
import com.threerings.bang.client.bui.IconPalette;
import com.threerings.bang.client.bui.SelectableIcon;
import com.threerings.bang.data.BangCodes;
import com.threerings.bang.data.BangBootstrapData;
import com.threerings.bang.data.CardItem;
import com.threerings.bang.data.GuestHandle;
import com.threerings.bang.data.Item;
import com.threerings.bang.data.PlayerObject;
import com.threerings.bang.util.BangContext;

import com.threerings.bang.avatar.client.ColorSelector;
import com.threerings.bang.avatar.util.AvatarLogic;

import com.threerings.bang.store.data.ArticleGood;
import com.threerings.bang.store.data.CardTripletGood;
import com.threerings.bang.store.data.Good;
import com.threerings.bang.store.data.GoodsObject;
import com.threerings.bang.store.data.SongGood;
import com.threerings.bang.store.data.StoreCodes;

/**
 * Displays detailed information on a particular good.
 */
public class GoodsInspector extends BContainer
    implements IconPalette.Inspector, ActionListener
{
    public GoodsInspector (BangContext ctx, GoodsPalette palette)
    {
        super(new AbsoluteLayout());
        _ctx = ctx;
        _palette = palette;
        _msgs = _ctx.getMessageManager().getBundle(StoreCodes.STORE_MSGS);

        add(_icon = new BLabel(""), new Rectangle(0, 0, 136, 156));
        int offset = getControlGapOffset();
        add(_title = new BLabel("", "medium_title"), new Rectangle(190 + offset, 115, 300, 40));
        _title.setFit(BLabel.Fit.SCALE);
        add(_descrip = new BLabel("", "goods_descrip"), new Rectangle(190 + offset, 55, 400, 60));

        // we'll add these later
        _ccont = GroupLayout.makeHBox(GroupLayout.LEFT);
        _ccont.add(new BLabel(_msgs.get("m.price"), "table_data"));
        _ccont.add(_cost = createCostLabel());
        _buy = new BButton(_msgs.get("m.buy"), this, "buy");
        _buy.setStyleClass("big_button");
    }

    /**
     * Gives us access to our store object when it is available.
     */
    public void init (GoodsObject goodsobj)
    {
        _goodsobj = goodsobj;
    }

    // documentation inherited from interface IconPalette.Inspector
    public void iconUpdated (SelectableIcon icon, boolean selected)
    {
        // nothing to do on deselection
        if (!selected) {
            return;
        }

        // make sure we're showing the buy interface
        showMode(Mode.BUY);
        _buy.setEnabled(!(_ctx.getUserObject().handle instanceof GuestHandle));

        // remove our color selectors
        removeColors();

        // configure our main interface with the good info
        _gicon = (GoodsIcon)icon;
        _good = _gicon.getGood();
        _title.setText(_ctx.xlate(BangCodes.GOODS_MSGS, _good.getName()));
        _descrip.setText(_ctx.xlate(BangCodes.GOODS_MSGS, _good.getTip()));
        updateCostLabel();

        // do some special jockeying to handle colorizations
        String[] cclasses = _good.getColorizationClasses(_ctx);
        if (cclasses != null && cclasses.length > 0) {
            _args[0] = _args[1] = _args[2] = Integer.valueOf(0);

            // grab whatever random colorizations we were using for the icon and start with those
            // in the inspector
            int[] colorIds = _gicon.colorIds;
            _zations = new Colorization[3];
            for (int ii = 0; ii < cclasses.length; ii++) {
                String cclass = cclasses[ii];
                if (cclass.equals(AvatarLogic.SKIN) ||
                    cclass.equals(AvatarLogic.HAIR)) {
                    continue;
                }

                // primary, secondary and tertiary colors have to go into the appropriate index
                int index = AvatarLogic.getColorIndex(cclass);
                ColorSelector colorsel = new ColorSelector(
                    _ctx, cclass, _palette.getColorEntity(), _colorpal);
                colorsel.setSelectedColorId(colorIds[index]);
                colorsel.setProperty("index", Integer.valueOf(index));
                add(_colorsel[index] = colorsel, CS_SPOTS[index]);
                _args[index] = Integer.valueOf(colorsel.getSelectedColor());
                _zations[index] = colorsel.getSelectedColorization();
            }

        } else {
            _zations = null;
        }

        updateImage();
    }

    // documentation inherited from interface ActionListener
    public void actionPerformed (ActionEvent event)
    {
        if (_good == null || _goodsobj == null) {
            return;
        }

        String action = event.getAction();
        if ("buy".equals(action)) {
            _buy.setEnabled(false);
            StoreService.ConfirmListener cl = new StoreService.ConfirmListener() {
                public void requestProcessed () {
                    boughtGood();
                }
                public void requestFailed (String cause) {
                    _buy.setEnabled(true);
                    _descrip.setText(_msgs.xlate(cause));
                    if (BangCodes.E_INSUFFICIENT_FUNDS.equals(cause) &&
                            _good.getCoinCost(_ctx.getUserObject()) > _ctx.getUserObject().coins) {
                        _ctx.getBangClient().displayPopup(new BankInfoWindow(), true, 350);
                    }
                }
            };
            _goodsobj.buyGood(_ctx.getClient(), _good.getType(), _args, cl);

        } else if ("try".equals(action)) {
            _try.setEnabled(false);
            BangBootstrapData bbd = (BangBootstrapData)_ctx.getClient().getBootstrapData();
            _ctx.getLocationDirector().moveTo(bbd.barberOid);

        } else if ("download".equals(action)) {
            String song = ((SongGood)_good).getSong();
            _ctx.getBangClient().displayPopup(new SongDownloadView(_ctx, song), true,
                                              SongDownloadView.PREF_WIDTH);
        }
    }

    protected MoneyLabel createCostLabel ()
    {
        return new MoneyLabel(_ctx);
    }

    protected void updateCostLabel ()
    {
        _cost.setMoney(_good.getScripCost(), _good.getCoinCost(_ctx.getUserObject()), false);
    }

    protected void boughtGood ()
    {
        // if they just bought cards, the "in your pocket" count
        if (_good instanceof CardTripletGood) {
            CardTripletGood ctg = (CardTripletGood)_good;
            PlayerObject pobj = _ctx.getUserObject();
            for (Item item : pobj.inventory) {
                if (item instanceof CardItem) {
                    CardItem ci = (CardItem)item;
                    if (ci.getType().equals(ctg.getCardType())) {
                        ctg.setQuantity(ci.getQuantity());
                    }
                }
            }
        }

        _palette.reinitGoods(true);

        // if they bought an article, give them a quick button to go try it on
        String msg = "m.purchased";
        if (_good instanceof ArticleGood) {
            showMode(Mode.TRY);
            msg += "_article";
        } else if (_good instanceof SongGood) {
            showMode(Mode.DOWNLOAD);
            msg += "_song";
        }

        _descrip.setText(_msgs.get(msg));
        BangUI.play(BangUI.FeedbackSound.ITEM_PURCHASE);
    }

    protected void showMode (Mode mode)
    {
        if (_mode == mode) {
            return;
        }

        safeRemove(_try);
        safeRemove(_ccont);
        safeRemove(_buy);
        safeRemove(_download);
        removeColors();

        int offset = getControlGapOffset();
        switch (_mode = mode) {
        case BUY:
            add(_ccont, new Rectangle(200 + offset, 15, 200, 25));
            add(_buy, new Point(400 + offset, 10));
            break;

        case TRY:
            if (_try == null) {
                _try = new BButton(_msgs.get("m.try"), this, "try");
                _try.setStyleClass("big_button");
            }
            add(_try, new Point(300 + offset, 10));
            break;

        case DOWNLOAD:
            if (_download == null) {
                _download = new BButton(
                    _msgs.get("m.download"), this, "download");
            }
            add(_download, new Point(300 + offset, 10));
            break;
        }
    }

    protected int getControlGapOffset ()
    {
        return 0;
    }

    protected void removeColors ()
    {
        // remove our color selectors
        for (int ii = 0; ii < _colorsel.length; ii++) {
            if (_colorsel[ii] != null) {
                remove(_colorsel[ii]);
                _colorsel[ii] = null;
            }
        }
    }

    protected void safeRemove (BComponent comp)
    {
        if (comp != null && comp.isAdded()) {
            remove(comp);
        }
    }

    protected void updateImage ()
    {
        _icon.setIcon(_good.createIcon(_ctx, _zations));
    }

    protected class BankInfoWindow extends BDecoratedWindow
        implements ActionListener
    {
        public BankInfoWindow ()
        {
            super(_ctx.getStyleSheet(), _msgs.get("m.binfo_title"));
            setModal(true);
            ((GroupLayout)getLayoutManager()).setGap(20);

            BLabel info = new BLabel(_msgs.get("m.binfo_info"));
            add(info);

            BContainer bcont = GroupLayout.makeHBox(GroupLayout.CENTER);
            ((GroupLayout)bcont.getLayoutManager()).setGap(25);
            bcont.add(new BButton(_msgs.get("m.to_bank"), this, "to_bank"));
            bcont.add(new BButton(_msgs.get("m.dismiss"), this, "dismiss"));
            add(bcont, GroupLayout.FIXED);
        }

        // from interface ActionListener
        public void actionPerformed (ActionEvent event)
        {
            _ctx.getBangClient().clearPopup(this, true);
            if ("to_bank".equals(event.getAction())) {
                BangBootstrapData bbd = (BangBootstrapData)_ctx.getClient().getBootstrapData();
                _ctx.getLocationDirector().moveTo(bbd.bankOid);
            }
        }
    }


    protected ActionListener _colorpal = new ActionListener() {
        public void actionPerformed (ActionEvent event) {
            ColorSelector colorsel = (ColorSelector)event.getSource();
            int index = (Integer)colorsel.getProperty("index");
            _args[index] = Integer.valueOf(colorsel.getSelectedColor());
            _zations[index] = colorsel.getSelectedColorization();
            updateImage();
            _gicon.setIcon(_icon.getIcon());
            _gicon.colorIds[index] = colorsel.getSelectedColor();
        }
    };

    protected BangContext _ctx;
    protected MessageBundle _msgs;
    protected GoodsObject _goodsobj;
    protected Good _good;
    protected GoodsIcon _gicon;

    protected BLabel _icon, _title, _descrip;
    protected BButton _buy, _try, _download;
    protected BContainer _ccont, _dcont;
    protected MoneyLabel _cost;

    protected GoodsPalette _palette;
    protected ColorSelector[] _colorsel = new ColorSelector[3];

    protected Object[] _args = new Object[3];
    protected Colorization[] _zations;

    protected static enum Mode { NEW, BUY, TRY, DOWNLOAD };
    protected Mode _mode = Mode.NEW;

    protected static final Point[] CS_SPOTS = {
        new Point(150, 105),
        new Point(150, 61),
        new Point(150, 17),
    };
}
