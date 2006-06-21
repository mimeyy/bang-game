//
// $Id$

package com.threerings.bang.game.data.effect;

import com.samskivert.util.ArrayUtil;
import com.samskivert.util.IntIntMap;

import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.piece.Piece;

import static com.threerings.bang.Log.log;

/**
 * The effect of activating a trap.
 */
public class TrapEffect extends BonusEffect
{
    /** The victim of the trap. */
    public int pieceId;
    
    /** The victim's new damage. */
    public int newDamage;
    
    /** If the victim dies, its death effect. */
    public Effect deathEffect;
    
    @Override // documentation inherited
    public void init (Piece piece)
    {
        pieceId = piece.pieceId;
    }

    @Override // documentation inherited
    public int[] getAffectedPieces ()
    {
        int[] pieces = new int[] { pieceId };
        if (deathEffect != null) {
            pieces = concatenate(pieces, deathEffect.getAffectedPieces());
        }
        return pieces;
    }
    
    @Override // documentation inherited
    public int[] getWaitPieces ()
    {
        return (deathEffect == null) ? NO_PIECES : deathEffect.getWaitPieces();
    }

    @Override // documentation inherited
    public void prepare (BangObject bangobj, IntIntMap dammap)
    {
        // determine the damage for the piece
        Piece target = bangobj.pieces.get(pieceId);
        if (target == null) {
            log.warning("Missing target for trap effect " +
                "[id=" + pieceId + "].");
            return;
        }
        int damage = getDamage(target);
        newDamage = target.damage + damage;
        dammap.increment(target.owner, damage);
        if (newDamage == 100) {
            deathEffect = target.willDie(bangobj, bonusId);
            if (deathEffect != null) {
                deathEffect.prepare(bangobj, dammap);
            }
        }
    }

    @Override // documentation inherited
    public boolean apply (BangObject bangobj, Observer obs)
    {
        // find out who owns the bonus
        int causer = -1;
        Piece bonus = bangobj.pieces.get(bonusId);
        if (bonus != null) {
            causer = bonus.owner;
        }
        
        // remove the bonus
        super.apply(bangobj, obs);

        Piece piece = bangobj.pieces.get(pieceId);
        if (piece == null) {
            log.warning("Missing target for trap effect " +
                        "[id=" + pieceId + "].");
            return false;
        }

        if (deathEffect != null) {
            deathEffect.apply(bangobj, obs);
        }
        damage(bangobj, obs, causer, piece, newDamage, ShotEffect.EXPLODED);
        
        return true;
    }
    
    /**
     * Returns the amount of damage to apply to the victim.
     */
    protected int getDamage (Piece piece)
    {
        return Math.min(50, 100-piece.damage);
    }
}
