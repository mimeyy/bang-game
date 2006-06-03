//
// $Id$

package com.threerings.bang.game.server.ai;

import java.awt.Point;

import com.threerings.bang.data.UnitConfig;

import com.threerings.bang.game.data.effect.NuggetEffect;
import com.threerings.bang.game.data.piece.Bonus;
import com.threerings.bang.game.data.piece.Claim;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.PieceCodes;
import com.threerings.bang.game.data.piece.Unit;
import com.threerings.bang.game.util.PointSet;

import static com.threerings.bang.Log.*;

/**
 * A simple AI for the claim jumping scenario.
 */
public class ClaimJumpingLogic extends AILogic
    implements PieceCodes
{
    // documentation inherited
    public String getBigShotType ()
    {
        // prefer a big shot with greater move distance
        UnitConfig[] configs = UnitConfig.getTownUnits(_bangobj.townId,
            UnitConfig.Rank.BIGSHOT);
        return getWeightedUnitTypes(configs, OFFENSE_EVALUATOR, 1)[0];
    }

    // documentation inherited
    public String[] getUnitTypes (int count)
    {
        UnitConfig[] configs = UnitConfig.getTownUnits(_bangobj.townId,
            UnitConfig.Rank.NORMAL);
        return getWeightedUnitTypes(configs, OFFENSE_EVALUATOR, count);
    }
    
    // documentation inherited
    protected void moveUnit (
        Piece[] pieces, Unit unit, PointSet moves, PointSet attacks)
    {
        // search for own claim, closest enemy claim with nuggets,
        // closest enemy with nugget, closest free nugget, and enemies
        // near our claim
        Claim oclaim = null, cclaim = null;
        Unit ctarget = null;
        Piece cnugget = null;
        boolean breached = false;
        for (int ii = 0; ii < pieces.length; ii++) {
            if (pieces[ii] instanceof Claim) {
                Claim claim = (Claim)pieces[ii];
                if (claim.owner == _pidx) {
                    oclaim = claim;
                } else if (claim.nuggets > 0 && (cclaim == null ||
                    unit.getDistance(claim) < unit.getDistance(cclaim))) {
                    cclaim = claim;
                }
            } else if (Bonus.isBonus(pieces[ii], NuggetEffect.NUGGET_BONUS)) {
                if (cnugget == null || unit.getDistance(pieces[ii]) <
                        unit.getDistance(cnugget)) {
                    cnugget = pieces[ii];
                }
            } else if (pieces[ii] instanceof Unit &&
                pieces[ii].owner != _pidx) {
                Unit target = (Unit)pieces[ii];
                if (target.benuggeted && (ctarget == null ||
                    unit.getDistance(target) < unit.getDistance(ctarget))) {
                    ctarget = target;
                }
                if (_claimloc != null && target.getDistance(_claimloc.x,
                    _claimloc.y) <= DEFENSIVE_PERIMETER) {
                    breached = true;                
                }
            }
        }
        if (_claimloc == null) {
            _claimloc = new Point(oclaim.x, oclaim.y);
        }
        
        // if we have a nugget or our claim is in danger, haul ass back home
        if ((unit.benuggeted || (breached && oclaim.nuggets > 0 &&
            unit.getDistance(oclaim) > DEFENSIVE_PERIMETER)) &&
            moveUnit(pieces, unit, moves, oclaim)) {
            return;
        
        // if there's a nugget within reach, grab it
        } else if (cnugget != null && moves.contains(cnugget.x, cnugget.y)) {
            executeOrder(unit, cnugget.x, cnugget.y, getBestTarget(
                pieces, unit, cnugget.x, cnugget.y, TARGET_EVALUATOR));
        
        // if there's a loaded claim within reach, steal from it
        } else if (cclaim != null && containsAdjacent(moves, cclaim) &&
            moveUnit(pieces, unit, moves, cclaim)) {
            return;
        
        // if there's a benuggeted target within reach, shoot it
        } else if (ctarget != null && attacks.contains(ctarget.x, ctarget.y)) {
            executeOrder(unit, Short.MAX_VALUE, 0, ctarget);
        
        // otherwise, move towards nearest free nugget
        } else if (cnugget != null && moveUnit(pieces, unit, moves, cnugget)) {
            return;
        
        // or nearest loaded claim
        } else if (cclaim != null && moveUnit(pieces, unit, moves, cclaim)) {
            return;
            
        // or nearest benuggeted target
        } else if (ctarget != null && moveUnit(pieces, unit, moves, ctarget)) {
            return;
            
        // or just try to find something to shoot 
        } else {
            Unit target = getBestTarget(pieces, unit, attacks,
                TARGET_EVALUATOR);
            if (target != null) {
                executeOrder(unit, Short.MAX_VALUE, 0, target);
            }
        }
    }
    
    /**
     * Attempts to move the unit towards the provided destination and fire
     * off a shot at the best target.
     *
     * @return true if we successfully moved towards the destination,
     * false if we couldn't find a path
     */
    protected boolean moveUnit (
        Piece[] pieces, Unit unit, PointSet moves, Piece target)
    {
        return moveUnit(pieces, unit, moves, target.x, target.y,
            TARGET_EVALUATOR);
    }
    
    /**
     * Determines whether the point set contains any points adjacent to the
     * given piece.
     */
    protected static boolean containsAdjacent (PointSet moves, Piece piece)
    {
        for (int ii = 0; ii < DIRECTIONS.length; ii++) {
            if (moves.contains(piece.x + DX[ii], piece.y + DY[ii])) {
                return true;
            }
        }
        return false;
    }
    
    /** The location of our own claim. */
    protected Point _claimloc;
    
    /** Ranks units by properties that should make them good at gathering
     * and stealing nuggets: speed and attack power. */
    protected static final UnitConfigEvaluator OFFENSE_EVALUATOR =
        new UnitConfigEvaluator() {
        public int getWeight (UnitConfig config) {
            return config.moveDistance*10 + config.damage;
        }
    };
    
    /** Ranks potential targets by benuggetedness, the amount of damage the
     * unit will do, and the amount of damage the target has already taken. */
    protected static final TargetEvaluator TARGET_EVALUATOR =
        new TargetEvaluator() {
        public int getWeight (Unit unit, Unit target) {
            UnitConfig.Rank rank = target.getConfig().rank;
            return (target.benuggeted ? 1000 : 0) +
                unit.computeScaledDamage(target) * 100 + target.damage;
        }
    };
    
    /** When enemy units get this close to our (non-empty) claim, we start
     * sending units to defend it. */
    protected static final int DEFENSIVE_PERIMETER = 5;
}
