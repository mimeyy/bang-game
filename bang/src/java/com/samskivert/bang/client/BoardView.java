//
// $Id$

package com.samskivert.bang.client;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import java.util.HashMap;
import java.util.Iterator;

import com.samskivert.swing.Label;

import com.threerings.presents.dobj.DEvent;
import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.EventListener;
import com.threerings.presents.dobj.SetListener;

import com.threerings.media.VirtualMediaPanel;
import com.threerings.media.sprite.LabelSprite;

import com.threerings.toybox.util.ToyBoxContext;

import com.samskivert.bang.client.sprite.PieceSprite;
import com.samskivert.bang.data.BangBoard;
import com.samskivert.bang.data.BangObject;
import com.samskivert.bang.data.ModifyBoardEvent;
import com.samskivert.bang.data.PiecePath;
import com.samskivert.bang.data.Terrain;
import com.samskivert.bang.data.piece.Piece;
import com.samskivert.bang.util.PointSet;

import static com.samskivert.bang.Log.log;
import static com.samskivert.bang.client.BangMetrics.*;

/**
 * Provides a shared base for displaying the board that is extended to
 * create the actual game view as well as the level editor.
 */
public class BoardView extends VirtualMediaPanel
    implements KeyListener
{
    public BoardView (ToyBoxContext ctx)
    {
        super(ctx.getFrameManager());
        _ctx = ctx;
    }

    /**
     * Sets up the board view with all the necessary bits. This is called
     * by the controller when we enter an already started game or the game
     * in which we're involved gets started.
     */
    public void startGame (BangObject bangobj, int playerIdx)
    {
        // clear out old piece sprites from a previous game
        for (Iterator<PieceSprite> iter = _pieces.values().iterator();
             iter.hasNext(); ) {
            removeSprite(iter.next());
            iter.remove();
        }

        // start afresh
        _bangobj = bangobj;
        _board = bangobj.board;
        dirtyScreenRect(new Rectangle(0, 0, getWidth(), getHeight()));

        // create sprites for all of the pieces
        for (Iterator iter = bangobj.pieces.iterator(); iter.hasNext(); ) {
            // this will trigger the creation, initialization and whatnot
            pieceUpdated(null, (Piece)iter.next());
        }

        // add the listener that will react to pertinent events
        bangobj.addListener(_blistener);

        // if the board is smaller than we are, center it in our view
        centerBoard();
    }

    /**
     * Called by the controller when our game has ended.
     */
    public void endGame ()
    {
        // remove our event listener
        _bangobj.removeListener(_blistener);

        // create a giant game over label and render it
        StringBuffer winners = new StringBuffer();
        for (int ii = 0; ii < _bangobj.winners.length; ii++) {
            if (_bangobj.winners[ii]) {
                if (winners.length() > 0) {
                    winners.append(", ");
                }
                winners.append(_bangobj.players[ii]);
            }
        }
        String wtext = "Game Over!\n";
        if (winners.length() > 0) {
            wtext += "Winners: " + winners;
        } else {
            wtext += "No winner!";
        }
        Label text = new Label(wtext, Color.white,
                               getFont().deriveFont(40f));
        text.setStyle(Label.OUTLINE);
        text.setAlternateColor(Color.black);
        text.setTargetWidth(300);
        text.layout(this);
        LabelSprite sprite = new LabelSprite(text);
        sprite.setRenderOrder(100);
        sprite.setLocation(
            _vbounds.x+(_vbounds.width-text.getSize().width)/2,
            _vbounds.y+(_vbounds.height-text.getSize().height)/2);
        addSprite(sprite);
    }

    // documentation inherited from interface KeyListener
    public void keyTyped (KeyEvent e)
    {
        // nothing doing
    }

    // documentation inherited from interface KeyListener
    public void keyPressed (KeyEvent e)
    {
        if (e.getKeyCode() == KeyEvent.VK_ALT &&
            _bangobj != null && _bangobj.isInPlay()) {
            showAttackSet();
        }
    }

    // documentation inherited from interface KeyListener
    public void keyReleased (KeyEvent e)
    {
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            clearAttackSet();
        }
    }

    // documentation inherited
    public void addNotify ()
    {
        super.addNotify();
        _ctx.getKeyDispatcher().addGlobalKeyListener(this);
    }

    // documentation inherited
    public void doLayout ()
    {
        super.doLayout();

        if (_board != null) {
            centerBoard();
        }
    }

    // documentation inherited
    public void removeNotify ()
    {
        super.removeNotify();
        _ctx.getKeyDispatcher().removeGlobalKeyListener(this);
    }

    // documentation inherited
    protected void paintBehind (Graphics2D gfx, Rectangle dirtyRect)
    {
        super.paintBehind(gfx, dirtyRect);

        // wait until we have some sort of board
        if (_board == null) {
            return;
        }

        // start with a black background
        gfx.setColor(Color.black);
        gfx.fill(dirtyRect);

        _pr.setLocation(0, 0);
        for (int yy = 0, hh = _board.getHeight(); yy < hh; yy++) {
            _pr.x = 0;
            for (int xx = 0, ww = _board.getWidth(); xx < ww; xx++) {
                if (dirtyRect.intersects(_pr)) {
                    Color color = getColor(_board.getTile(xx, yy));
                    gfx.setColor(color);
                    gfx.fill(_pr);
                    gfx.setColor(Color.black);
                    gfx.draw(_pr);
                    if (xx == _mouse.x && yy == _mouse.y) {
                        gfx.setColor(Color.white);
                        gfx.drawOval(
                            _pr.x+2, _pr.y+2, _pr.width-4, _pr.height-4);
                    }
                }
                _pr.x += SQUARE;
            }
            _pr.y += SQUARE;
        }
    }

    // documentation inherited
    protected void paintInFront (Graphics2D gfx, Rectangle dirtyRect)
    {
        super.paintInFront(gfx, dirtyRect);

        // render our attack or attention sets
        if (_attackSet != null) {
            renderSet(gfx, dirtyRect, _attackSet, Color.blue);
        }
        if (_attentionSet != null) {
            renderSet(gfx, dirtyRect, _attentionSet, Color.green);
        }
    }

    /** Relocates our virtual view to center the board iff it is smaller
     * than the viewport. */
    protected void centerBoard ()
    {
        int width = _board.getWidth() * SQUARE,
            height = _board.getHeight() * SQUARE;
        int nx = _vbounds.x, ny = _vbounds.y;
        if (width < _vbounds.width) {
            nx = (width - _vbounds.width) / 2;
        }
        if (height < _vbounds.height) {
            ny = (height - _vbounds.height) / 2;
        }
        setViewLocation(nx, ny);
    }

    /** Highlights a set of tiles in the specified color. */
    protected void renderSet (Graphics2D gfx, Rectangle dirtyRect,
                              PointSet set, Color color)
    {
        _pr.setLocation(0, 0);
        Composite ocomp = gfx.getComposite();
        gfx.setComposite(SET_ALPHA);
        for (int ii = 0, ll = set.size(); ii < ll; ii++) {
            _pr.x = set.getX(ii) * SQUARE;
            _pr.y = set.getY(ii) * SQUARE;
            if (dirtyRect.intersects(_pr)) {
                gfx.setColor(color);
                gfx.fillRoundRect(
                    _pr.x+2, _pr.y+2, _pr.width-4, _pr.height-4, 8, 8);
            }
        }
        gfx.setComposite(ocomp);
    }

    /** Invalidates a tile, causing it to be redrawn on the next tick. */
    protected void invalidateTile (int xx, int yy)
    {
        _remgr.invalidateRegion(xx * SQUARE, yy * SQUARE, SQUARE, SQUARE);
    }

    /**
     * Updates the tile over which we know the mouse to be.
     *
     * @return true if the specified new mouse coordinates are different
     * from the previously known coordinates, false if they are the same.
     */
    protected boolean updateMouseTile (int mx, int my)
    {
        if (mx != _mouse.x || my != _mouse.y) {
            invalidateTile(_mouse.x, _mouse.y);
            _mouse.x = mx;
            _mouse.y = my;
            invalidateTile(_mouse.x, _mouse.y);
            return true;
        }
        return false;
    }

    protected Color getColor (Terrain tile)
    {
        Color color = null;
        switch (tile) {
        case DIRT: color = Color.orange.darker(); break;
        case MOSS: color = Color.green.darker(); break;
        case TALL_GRASS: color = Color.green; break;
        case WATER: color = Color.blue; break;
        case LEAF_BRIDGE: color = Color.lightGray; break;
        default: color = Color.black; break;
        }
        return color;
    }

    /**
     * Returns (creating if necessary) the piece sprite associated with
     * the supplied piece. A newly created sprite will automatically be
     * initialized with the supplied piece and added to the board view.
     */
    protected PieceSprite getPieceSprite (Piece piece)
    {
        PieceSprite sprite = _pieces.get(piece.pieceId);
        if (sprite == null) {
            sprite = piece.createSprite();
            sprite.init(_ctx, piece);
            _pieces.put((int)piece.pieceId, sprite);
            addSprite(sprite);
        }
        return sprite;
    }

    protected void showAttackSet ()
    {
        _attackSet = new PointSet();
        _attentionSet = new PointSet();
        for (Iterator iter = _bangobj.pieces.iterator(); iter.hasNext(); ) {
            Piece piece = (Piece)iter.next();
            PieceSprite sprite = _pieces.get(piece.pieceId);
            if (sprite != null && isManaged(sprite)) {
                piece.enumerateAttacks(_attackSet);
                piece.enumerateAttention(_attentionSet);
            }
        }
        _remgr.invalidateRegion(_vbounds);
    }

    protected void clearAttackSet ()
    {
        _attackSet = null;
        _attentionSet = null;
        _remgr.invalidateRegion(_vbounds);
    }

    protected void dirtyPath (PiecePath path)
    {
        if (path != null) {
            for (int ii = 0, ll = path.getLength(); ii < ll; ii++) {
                dirtyTile(path.getX(ii), path.getY(ii));
            }
        }
    }

    protected void dirtySet (PointSet set)
    {
        for (int ii = 0, ll = set.size(); ii < ll; ii++) {
            dirtyTile(set.getX(ii), set.getY(ii));
        }
    }

    protected void dirtyTile (int tx, int ty)
    {
        _remgr.invalidateRegion(tx*SQUARE, ty*SQUARE, SQUARE, SQUARE);
    }

    /** Called when a piece is updated in the game object. */
    protected void pieceUpdated (Piece opiece, Piece npiece)
    {
        getPieceSprite(npiece).updated(npiece);
    }        

    /** Listens for various different events and does the right thing. */
    protected class BoardEventListener
        implements SetListener, EventListener
    {
        public void entryAdded (EntryAddedEvent event) {
            if (event.getName().equals(BangObject.PIECES)) {
                getPieceSprite((Piece)event.getEntry());
            }
        }

        public void entryUpdated (EntryUpdatedEvent event) {
            if (event.getName().equals(BangObject.PIECES)) {
                pieceUpdated((Piece)event.getOldEntry(),
                             (Piece)event.getEntry());
            }
        }

        public void entryRemoved (EntryRemovedEvent event) {
            if (event.getName().equals(BangObject.PIECES)) {
                PieceSprite sprite = _pieces.remove((Integer)event.getKey());
                if (sprite != null) {
                    sprite.removed();
                } else {
                    log.warning("No sprite for removed piece " +
                                "[id=" + event.getKey() + "].");
                }
            }
        }

        public void eventReceived (DEvent event) {
            if (event instanceof ModifyBoardEvent) {
                // dirty the square in question
                ModifyBoardEvent mevent = (ModifyBoardEvent)event;
                invalidateTile(mevent.x, mevent.y);
            }
        }
    };

    protected ToyBoxContext _ctx;

    protected BangObject _bangobj;
    protected BangBoard _board;
    protected BoardEventListener _blistener = new BoardEventListener();

    protected PointSet _attackSet, _attentionSet;

    /** The current tile coordinates of the mouse. */
    protected Point _mouse = new Point(-1, -1);

    protected HashMap<Integer,PieceSprite> _pieces =
        new HashMap<Integer,PieceSprite>();

    /** Used during rendering. */
    protected Rectangle _pr = new Rectangle(0, 0, SQUARE, SQUARE);

    /** The alpha level used to render a set of pieces. */
    protected static final Composite SET_ALPHA =
        AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f);
}
