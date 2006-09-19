//
// $Id$

package com.threerings.bang.game.data.piece;

import java.util.ArrayList;

import com.samskivert.util.ArrayUtil;

import com.threerings.bang.data.UnitConfig;

import com.threerings.bang.game.client.sprite.CowSprite;
import com.threerings.bang.game.client.sprite.PieceSprite;
import com.threerings.bang.game.data.BangBoard;
import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.effect.Effect;
import com.threerings.bang.game.data.effect.SpookEffect;
import com.threerings.bang.game.data.scenario.CattleRustlingInfo;
import com.threerings.bang.game.util.PieceUtil;
import com.threerings.bang.game.util.PointSet;

import static com.threerings.bang.Log.log;

/**
 * Handles the behavior of the cow piece which is used in cattle rustling and
 * other scenarios.
 */
public class Cow extends Piece
{
    /** Indicates whether or not this cow has been corralled. */
    public boolean corralled;

    /**
     * Called when a unit moves next to this cow or the cow was part of
     * a mass spooking; causes the cow to move away from the spooker.
     *
     * @param herd if true, the cow was spooked as part of a herd, and was
     * not branded
     */
    public SpookEffect spook (BangObject bangobj, Piece spooker, boolean herd)
    {
        // if we were spooked by a big shot, become owned by that player
        int owner = -1;
        if (spooker instanceof Unit && !herd) {
            Unit unit = (Unit)spooker;
            if (unit.getConfig().rank == UnitConfig.Rank.BIGSHOT) {
                if (this.owner != -1) {
                    bangobj.grantPoints(
                        this.owner, -CattleRustlingInfo.POINTS_PER_COW);
                }
                setOwner(bangobj, spooker.owner);
                bangobj.grantPoints(owner, CattleRustlingInfo.POINTS_PER_COW);
            }
        }

        // run in the opposite direction of our spooker
        return move(bangobj, (PieceUtil.getDirection(this, spooker) + 2) % 4,
            owner, spooker.pieceId);
    }

    @Override // documentation inherited
    public PieceSprite createSprite ()
    {
        return new CowSprite();
    }

    @Override // documentation inherited
    public int getMoveDistance ()
    {
        return 4;
    }

    @Override // documentation inherited
    public int getGoalRadius (Piece mover)
    {
        return (mover.owner != owner && mover instanceof Unit &&
            ((Unit)mover).getConfig().rank == UnitConfig.Rank.BIGSHOT) ?
                +1 : -1;
    }
    
    @Override // documentation inherited
    public ArrayList<Effect> tick (
            short tick, BangObject bangobj, Piece[] pieces)
    {
        // if we're corralled, stop moving
        if (corralled) {
            return null;
        }

        // if we're walled in on all three sides, we want to move
        int walls = 0, direction = -1;
        for (int dd = 0; dd < DIRECTIONS.length; dd++) {
            if (bangobj.board.isGroundOccupiable(x + DX[dd], y + DY[dd])) {
                // in the case that we're walled in on three sides, this will
                // only get assigned once, to the direction in which we are not
                // walled in
                direction = dd;
            } else {
                walls++;
            }
        }
        if (walls < 3 || direction == -1) {
            return null;
        }

        ArrayList<Effect> effects = new ArrayList<Effect>();
        effects.add(move(bangobj, direction, -1, -1));
        return effects;
    }

    protected SpookEffect move (BangObject bangobj, int direction,
                                int owner, int spookerId)
    {
        // otherwise look around for somewhere nicer to stand
        _moves.clear();
        bangobj.board.computeMoves(this, _moves, null);
        int[] coords = _moves.toIntArray();
        ArrayUtil.shuffle(coords);

        // move any coords containing train tracks to the end
        int tidx = coords.length;
        for (int ii = 0; ii < tidx; ) {
            if (bangobj.getTracks().containsKey(coords[ii])) {
                int tmp = coords[--tidx];
                coords[tidx] = coords[ii];
                coords[ii] = tmp;
            } else {
                ii++;
            }
        }
        
        // first look for a coordinate in the direction that we want to move
        // (that's not a track)
        int nx = x, ny = y;
        for (int ii = 0; ii < tidx; ii++) {
            int hx = PointSet.decodeX(coords[ii]);
            int hy = PointSet.decodeY(coords[ii]);
            if (whichDirection(hx, hy) == direction) {
                nx = hx;
                ny = hy;
                break;
            }
        }

        // if that failed, go with anything that works
        if (nx == x && ny == y && coords.length > 0) {
            nx = PointSet.decodeX(coords[0]);
            ny = PointSet.decodeY(coords[0]);
        }

        SpookEffect spook = new SpookEffect();
        spook.init(this);
        spook.owner = owner;
        spook.spookerId = spookerId;
        spook.nx = (short)nx;
        spook.ny = (short)ny;
        return spook;
    }

    protected int whichDirection (int nx, int ny)
    {
        if (nx == x) {
            return (ny < y) ? NORTH : SOUTH;
        } else if (ny == y) {
            return (nx < x) ? WEST : EAST;
        }
        return -1;
    }

    /** Used for temporary calculations. */
    protected static PointSet _moves = new PointSet();
}
