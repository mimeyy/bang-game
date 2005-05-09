//
// $Id$

package com.threerings.bang.client;

import java.util.ArrayList;

import com.jme.bui.BButton;
import com.jme.bui.BContainer;
import com.jme.bui.BLabel;
import com.jme.bui.BTextField;
import com.jme.bui.BWindow;
import com.jme.bui.TintedBackground;
import com.jme.bui.event.ActionEvent;
import com.jme.bui.event.ActionListener;
import com.jme.bui.layout.BorderLayout;
import com.jme.bui.layout.TableLayout;
import com.jme.renderer.ColorRGBA;

import com.samskivert.util.StringUtil;

import com.threerings.util.MessageBundle;

import com.threerings.bang.data.piece.Artillery;
import com.threerings.bang.data.piece.Dirigible;
import com.threerings.bang.data.piece.Gunslinger;
import com.threerings.bang.data.piece.Piece;
import com.threerings.bang.data.piece.SteamGunman;

import com.threerings.bang.data.BangCodes;
import com.threerings.bang.data.BangConfig;
import com.threerings.bang.data.BangObject;
import com.threerings.bang.util.BangContext;

import static com.threerings.bang.Log.log;

/**
 * Displays an interface for purchasing units.
 */
public class PurchaseView extends BWindow
    implements ActionListener
{
    public PurchaseView (BangContext ctx, BangConfig config,
                         BangObject bangobj, int pidx)
    {
        super(ctx.getLookAndFeel(), new BorderLayout(2, 2));

        setBackground(new TintedBackground(
                          0, 0, 0, 0, new ColorRGBA(0, 0, 0, 0.5f)));

        _ctx = ctx;
        _msgs = _ctx.getMessageManager().getBundle(BangCodes.BANG_MSGS);
        _bangobj = bangobj;
        _pidx = pidx;

        BContainer header = new BContainer(new BorderLayout(10, 0));
        header.add(new BLabel(_msgs.get("m.buying_phase")), BorderLayout.WEST);
        String rmsg = _msgs.get(
            "m.round", ""+_bangobj.roundId, ""+config.rounds);
        header.add(new BLabel(rmsg), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        BContainer units =
            new BContainer(new TableLayout(COLUMNS.length, 2, 2));
        for (int ii = 0; ii < COLUMNS.length; ii++) {
            String col = COLUMNS[ii];
            if (!StringUtil.blank(col)) {
                col = _msgs.get("m.col_" + col);
            }
            units.add(new BLabel(col));
        }
        for (int ii = 0; ii < UNIT_IDENTS.length; ii++) {
            addUnitRow(units, ii);
        }
        add(units, BorderLayout.CENTER);

        BContainer footer = new BContainer(new BorderLayout(10, 0));
        _tlabel = new BLabel(_msgs.get("m.total_cost", "" + 0));
        footer.add(_tlabel, BorderLayout.WEST);

        _ready = new BButton(_msgs.get("m.ready"));
        _ready.addListener(this);
//         _ready.setEnabled(false);
        footer.add(_ready, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    protected void addUnitRow (BContainer cont, final int index)
    {
        cont.add(new BLabel(_msgs.get("m." + UNIT_IDENTS[index])));
        BLabel cost = new BLabel("" + UNIT_PROTOS[index].getCost());
        cost.setHorizontalAlignment(BLabel.RIGHT);
        cont.add(cost);
        cont.add(_qlabels[index] = new BLabel("0"));
        _qlabels[index].setHorizontalAlignment(BLabel.RIGHT);
        BButton up = new BButton("^");
        up.addListener(new ActionListener() {
            public void actionPerformed (ActionEvent e) {
                setQuantity(index, _quants[index]+1);
            }
        });
        cont.add(up);
        BButton down = new BButton("v");
        down.addListener(new ActionListener() {
            public void actionPerformed (ActionEvent e) {
                setQuantity(index, _quants[index]-1);
            }
        });
        cont.add(down);
    }

    protected void setQuantity (int index, int value)
    {
        value = Math.max(value, 0);
        _quants[index] = value;
        _qlabels[index].setText(String.valueOf(_quants[index]));
        updateTotal();
    }

    protected void updateTotal ()
    {
        _total = 0;
        for (int ii = 0; ii < _quants.length; ii++) {
            _total += _quants[ii] * UNIT_PROTOS[ii].getCost();
        }
        _tlabel.setText(_msgs.get("m.total_cost", "" + _total));
    }

    // documentation inherited from interface ActionListener
    public void actionPerformed (ActionEvent e)
    {
        ArrayList<Piece> pieces = new ArrayList<Piece>();
        for (int ii = 0; ii < UNIT_PROTOS.length; ii++) {
            for (int pp = 0; pp < _quants[ii]; pp++) {
                pieces.add((Piece)UNIT_PROTOS[ii].clone());
            }
        }
        Piece[] pvec = pieces.toArray(new Piece[pieces.size()]);
        _bangobj.service.purchasePieces(_ctx.getClient(), pvec);
    }

    protected BangContext _ctx;
    protected MessageBundle _msgs;
    protected BangObject _bangobj;
    protected int _pidx;

    protected int[] _quants = new int[UNIT_IDENTS.length];
    protected BLabel[] _qlabels = new BLabel[UNIT_IDENTS.length];
    protected int _total;
    protected BLabel _tlabel;
    protected BButton _ready;

    protected static final String[] COLUMNS = {
        "unit", "cost", "count", "", ""
    };
    protected static final String[] UNIT_IDENTS = {
        "steamgunman", "artillery", "dirigible", "gunslinger"
    };
    protected static final Piece[] UNIT_PROTOS = {
        new SteamGunman(), new Artillery(), new Dirigible(), new Gunslinger()
    };
}
