//
// $Id$

package com.threerings.bang.game.server.scenario;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import java.awt.Rectangle;

import com.samskivert.util.IntListUtil;
import com.samskivert.util.RandomUtil;

import com.threerings.bang.data.Stat;

import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.effect.CountEffect;
import com.threerings.bang.game.data.effect.FadeBoardEffect;
import com.threerings.bang.game.data.effect.TalismanEffect;
import com.threerings.bang.game.data.effect.ToggleSwitchEffect;
import com.threerings.bang.game.data.effect.WendigoEffect;
import com.threerings.bang.game.data.piece.Counter;
import com.threerings.bang.game.data.piece.Marker;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.PieceCodes;
import com.threerings.bang.game.data.piece.ToggleSwitch;
import com.threerings.bang.game.data.piece.Unit;
import com.threerings.bang.game.data.piece.Wendigo;
import com.threerings.bang.game.data.scenario.WendigoAttackInfo;
import com.threerings.bang.game.util.PointSet;

/**
 * Handles the heavy lifting for the {@link WendigoAttack} scenario.
 */
public class WendigoDelegate extends CounterDelegate
    implements PieceCodes
{
    /**
     * Returns the set of active safe points.
     */
    public PointSet getSafePoints ()
    {
        return _safePoints[_activeSafePoints];
    }

    /**
     * Returns true if the wendigo are created and ready for deployment.
     */
    public boolean wendigoReady ()
    {
        return (_wendigo != null);
    }

    /**
     * Create several wendigo that will spawn just outside the playfield. Also
     * fades the board to let the players know the wendigo are coming. This
     * should be called prior to deploying them via {@link #deployWendigo}.
     */
    public void createWendigo (BangObject bangobj, short tick)
    {
        // fade the board to let the players know the wendigo are coming
        _bangmgr.deployEffect(-1, new FadeBoardEffect());

        Rectangle playarea = bangobj.board.getPlayableArea();
        // First decide horizontal or vertical attack
        boolean horiz = (RandomUtil.getInt(2) == 0);
        int num = (horiz ? playarea.height : playarea.width) / 2;
        _wendigo = new ArrayList<Wendigo>(num);
        int off = 0;
        int length = 0;
        if (horiz) {
            off = playarea.y;
            length = playarea.height - 1;
        } else {
            off = playarea.x;
            length = playarea.width - 1;
        }

        // pick the set of tiles to attack based on the number of units
        // in the attack zone
        int[] weights = new int[length];
        Arrays.fill(weights, 1);
        Piece[] pieces = bangobj.getPieceArray();
        for (Piece p : pieces) {
            if (p instanceof Unit && p.isAlive()) {
                int coord = (horiz ? p.y : p.x) - off;
                if (coord < length) {
                    weights[coord]++;
                }
                if (coord - 1 >= 0) {
                    weights[coord - 1]++;
                }
            }
        }

        // generate the wendigo spread out along the edge
        for (int ii = 0; ii < num; ii++) {
            int idx = (IntListUtil.sum(weights) == 0 ?
                RandomUtil.getInt(length) :
                RandomUtil.getWeightedIndex(weights));
            weights[idx] = 0;
            boolean side = RandomUtil.getInt(2) == 0;
            int cidx = idx, ridx = idx, lidx = idx;
            boolean leftside = false, rightside = false;
            boolean arms = RandomUtil.getInt(3) != 0;
            if (idx - 1 >= 0) {
                if (arms && idx - 2 >= 0 && weights[idx - 2] > 0) {
                    rightside = true;
                    ridx = idx - 2;
                }
                weights[idx - 1] = 0;
            }
            if (idx + 1 < weights.length) {
                if (arms && idx + 2 < weights.length &&
                        weights[idx + 2] > 0) {
                    leftside = true;
                    lidx = idx + 2;
                }
                weights[idx + 1] = 0;
            }
            if (rightside || leftside) {
                num--;
                if (leftside && rightside) {
                    num--;
                } else if (leftside) {
                    ridx = idx;
                    cidx++;
                } else {
                    lidx = cidx;
                    cidx--;
                }
                num--;
                weights[ridx] = 0;
                if (ridx - 1 >= 0) {
                    weights[idx - 1] = 0;
                }
                weights[lidx] = 0;
                if (lidx + 1 < weights.length) {
                    weights[lidx + 1] = 0;
                }
            }
            createWendigo(bangobj, cidx + off, horiz, side,
                    playarea, false, tick);
            if (leftside || rightside) {
                createWendigo(bangobj, ridx + off, horiz, side,
                        playarea, true, tick);
                createWendigo(bangobj, lidx + off, horiz, side,
                        playarea, true, tick);
            }
            int sum = 0;
            for (int weight : weights) {
                sum += weight;
            }
            if (sum == 0) {
                break;
            }
        }
    }

    /**
     * Sends in the Wendigo.
     */
    public void deployWendigo (BangObject bangobj, short tick)
    {
        for (Wendigo wendigo : _wendigo) {
            _bangmgr.addPiece(wendigo);
        }
        WendigoEffect effect =
            WendigoEffect.wendigoAttack(bangobj, _wendigo);
        effect.safePoints = getSafePoints();
        _wendigoRespawnTicks = new int[bangobj.players.length];
        Arrays.fill(_wendigoRespawnTicks, 3);
        _bangmgr.deployEffect(-1, effect);
        _wendigoRespawnTicks = null;
        updatePoints(bangobj);
        _wendigo = null;
    }

    @Override // documentation inherited
    public void filterPieces (
        BangObject bangobj, ArrayList<Piece> starts,
        ArrayList<Piece> pieces, ArrayList<Piece> updates)
    {
        super.filterPieces(bangobj, starts, pieces, updates);

        // extract and remove all the safe spots
        _safePoints[0].clear();
        _safePoints[1].clear();
        for (Iterator<Piece> iter = pieces.iterator(); iter.hasNext(); ) {
            Piece p = iter.next();
            if (Marker.isMarker(p, Marker.SAFE)) {
                _safePoints[0].add(p.x, p.y);
                // we don't remove the markers here since we want to assign
                // it a pieceId

            } else if (Marker.isMarker(p, Marker.SAFE_ALT)) {
                _safePoints[1].add(p.x, p.y);

            } else if (p instanceof ToggleSwitch) {
                _toggleSwitches.add((ToggleSwitch)p);
            }
        }
    }

    @Override // documentation inherited
    public void pieceMoved (BangObject bangobj, Piece piece)
    {
        checkTSActivation(piece, bangobj.tick);

        for (ToggleSwitch ts : _toggleSwitches) {
            if (ts.x == piece.x && ts.y == piece.y) {
                ToggleSwitchEffect effect = new ToggleSwitchEffect();
                effect.switchId = ts.pieceId;
                effect.occupier = piece.pieceId;
                if (ts.isActive(bangobj.tick)) {
                    _activeSafePoints = (_activeSafePoints + 1) % 2;
                    effect.state = getTSState();
                    effect.activator = piece.pieceId;
                }
                _bangmgr.deployEffect(-1, effect);
                break;
            }
        }
    }

    @Override // documentation inherited
    public void pieceWasKilled (BangObject bangobj, Piece piece, int shooter)
    {
        // a dirigible may activate a toggle on dying
        checkTSActivation(piece, bangobj.tick);
    }

    @Override // documentation inherited
    protected int pointsPerCounter ()
    {
        return WendigoAttackInfo.POINTS_PER_SURVIVAL;
    }

    @Override // documentation inherited
    protected void checkAdjustedCounter (BangObject bangobj, Unit unit)
    {
        // nothing to do here
    }

    protected ToggleSwitch.State getTSState ()
    {
        return (_activeSafePoints == 0 ?
                ToggleSwitch.State.SQUARE : ToggleSwitch.State.CIRCLE);
    }

    /**
     * Checks for the activation of a toggle switch.
     */
    protected void checkTSActivation (Piece piece, short tick)
    {
        for (ToggleSwitch ts : _toggleSwitches) {
            if (piece.pieceId == ts.occupier) {
                ToggleSwitchEffect effect = new ToggleSwitchEffect();
                effect.switchId = ts.pieceId;
                effect.occupier = piece.pieceId;
                if (piece.pieceId == ts.activator) {
                    effect.tick = tick;
                } else {
                    effect.tick = -1;
                }
                _bangmgr.deployEffect(-1, effect);
            }
        }
    }

    protected void createWendigo (
            BangObject bangobj, int idx, boolean horiz, boolean side,
            Rectangle playarea, boolean claw, short tick)
    {
        Wendigo wendigo = new Wendigo(claw);
        wendigo.assignPieceId(bangobj);
        int orient = NORTH;
        if (horiz) {
            orient = (side) ? EAST : WEST;
            wendigo.position(playarea.x +
                    (orient == EAST ? -4 : playarea.width + 2),
                idx);
        } else {
            orient = (side) ? NORTH : SOUTH;
            wendigo.position(idx, playarea.y +
                    (orient == SOUTH ? -4 : playarea.height + 2));
        }
        wendigo.orientation = (short)orient;
        wendigo.lastActed = tick;
        _wendigo.add(wendigo);
    }

    /**
     * Grant points for surviving units after a wendigo attack.
     */
    protected void updatePoints (BangObject bangobj)
    {
        int[] points = new int[bangobj.players.length];
        int[] talpoints = new int[bangobj.players.length];
        boolean[] teamSurvival = new boolean[bangobj.players.length];
        Arrays.fill(teamSurvival, true);

        Piece[] pieces = bangobj.getPieceArray();
        for (Piece p : pieces) {
            if (p instanceof Unit && p.owner > -1) {
                if (p.isAlive()) {
                    points[p.owner]++;
                    if (TalismanEffect.TALISMAN_BONUS.equals(
                            ((Unit)p).holding) &&
                        _safePoints[_activeSafePoints].contains(
                            p.x, p.y)) {
                        talpoints[p.owner]++;
                    }
                } else {
                    teamSurvival[p.owner] = false;
                }
            }
        }

        bangobj.startTransaction();
        try {
            for (int idx = 0; idx < points.length; idx++) {
                if (points[idx] > 0) {
                    int talpts = talpoints[idx] * TALISMAN_SAFE;
                    bangobj.grantPoints(
                        idx, points[idx] * pointsPerCounter() + talpts);
                    bangobj.stats[idx].incrementStat(
                        Stat.Type.WENDIGO_SURVIVALS, points[idx]);
                    bangobj.stats[idx].incrementStat(
                        Stat.Type.TALISMAN_POINTS, talpts);
                    bangobj.stats[idx].incrementStat(
                        Stat.Type.TALISMAN_SPOT_SURVIVALS, talpoints[idx]);
                }
            }
        } finally {
            bangobj.commitTransaction();
        }

        if (_counters.size() == 0) {
            return;
        }

        int queuePiece = _wendigo.get(0).pieceId;
        for (Counter counter : _counters) {
            if (points[counter.owner] > 0) {
                _bangmgr.deployEffect(
                        -1, CountEffect.changeCount(counter.pieceId,
                            counter.count + points[counter.owner],
                            queuePiece));
            }
        }
    }

    /** Our wendigo. */
    protected ArrayList<Wendigo> _wendigo;

    /** The tick when the wendigo will attack. */
    protected short _attackTick;

    /** Current wendigo attack number. */
    protected int _numAttacks;

    /** Respawn ticks for units. */
    protected int[] _wendigoRespawnTicks;

    /** Reference to the toggle switches. */
    protected ArrayList<ToggleSwitch> _toggleSwitches =
        new ArrayList<ToggleSwitch>();

    /** Which set of safepoints are currently active. */
    protected int _activeSafePoints = 0;

    /** Set of the sacred location markers. */
    protected PointSet[] _safePoints = new PointSet[] {
        new PointSet(), new PointSet() };

    /** Number of points for having a talisman on a safe zone. */
    protected static final int TALISMAN_SAFE = 40;
}