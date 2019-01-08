package com.uwaterloo.watcag.router.elements;

import com.uwaterloo.watcag.util.RouteUtil;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

import java.util.*;

public class RouteTemplate {

    /*
     * Wrapper around an ArrayList of WireJunctions, which describe the hops needed to complete a route
     */

    // True for all Ultrascale+; 16 for Ultrascale
    private static final int LONG_LINE_LENGTH = 6;

    private int baseCost;
    private int adjustedCost;
    private int orthogonalTurns;
    private WireDirection lastDirection;

    private EnterWireJunction src;
    private ExitWireJunction snk;

    private int distanceX;
    private int distanceY;

    // Inclusive of src and snk
    private ArrayList<WireJunction> template;

    public RouteTemplate(Design d, EnterWireJunction src, ExitWireJunction snk) {
        orthogonalTurns = 0;
        baseCost = 0;
        adjustedCost = 0;
        lastDirection = null;

        this.src = src;
        this.snk = snk;

        Tile srcIntTile = d.getDevice().getTile(src.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snk.getTileName());

        distanceX = snkIntTile.getTileXCoordinate() - srcIntTile.getTileXCoordinate();
        distanceY = snkIntTile.getTileYCoordinate() - srcIntTile.getTileYCoordinate();

        template = new ArrayList<>();
        template.add(src);
        template.add(snk);
    }

    /*
     * Copy constructor
     */
    public RouteTemplate(RouteTemplate ref) {
        baseCost = ref.baseCost;
        adjustedCost = ref.getAdjustedCost();
        orthogonalTurns = ref.getNumOrthogonalTurns();

        src = ref.getSrc();
        snk = ref.getSnk();

        distanceX = ref.getDistanceX();
        distanceY = ref.getDistanceY();

        template = new ArrayList<>(ref.getTemplate());
    }

    public RouteTemplate copyWithOffset(Design d, int dx, int dy) {
        RouteTemplate copy = new RouteTemplate(d, src.copyWithOffset(d, dx, dy), snk.copyWithOffset(d, dx, dy));
        for (int i = 1; i < template.size() - 1; i++) {
            copy.getTemplate().add(i, template.get(i).copyWithOffset(d, dx, dy));
        }
        return copy;
    }

    // Gets cost factoring readjustment score
    public int getAdjustedCost() {
        return adjustedCost;
    }

    public void setBaseAdjustedCost(int baseCost) {
        adjustedCost = baseCost;
    }

    public int getNumOrthogonalTurns() {
        return orthogonalTurns;
    }

    private void readjustCost(Design d, EnterWireJunction enJunc) {

        int delta = 2;

        // Add costs for tile path traversal
        if (enJunc.getTilePathCost() != -1)
            delta += enJunc.getTilePathCost();
        if (enJunc.getSrcJunction(d).getTilePathCost() != -1)
            delta += enJunc.getSrcJunction(d).getTilePathCost();

        // Punish short hops: short hops tend to be more expensive to route
        if (enJunc.getWireLength() < LONG_LINE_LENGTH)
            delta += 4;
        // Punish reversals: although sometimes they are necessary
        if (RouteUtil.reverseDirection(enJunc.getDirection()).equals(lastDirection))
            delta += 2;
        // Punish orthogonal turns: these routes seem to be very slow
        else if (RouteUtil.isOrthogonal(enJunc.getDirection(), lastDirection)) {
            delta += 8;
            orthogonalTurns += 1;
        }

        adjustedCost += delta;
    }

    private void readjustCost(Design d) {
        adjustedCost = baseCost;
        for (int i = 2; i < template.size(); i += 2) {
            readjustCost(d, (EnterWireJunction) template.get(i));
        }
    }

    public EnterWireJunction getSrc() {
        return src;
    }

    public ExitWireJunction getSnk() {
        return snk;
    }

    public int getDistanceX() {
        return distanceX;
    }

    public int getDistanceY() {
        return distanceY;
    }

    public ArrayList<WireJunction> getTemplate() {
        return template;
    }

    public WireJunction getTemplate(int i) {
        if (i < 0)
            i += template.size();
        return template.get(i);
    }

    public boolean isEmpty() {
        return template.isEmpty();
    }

    public void pushEnterWireJunction(Design d, EnterWireJunction enJunc) {
        template.add(1, enJunc);
        template.add(1, enJunc.getSrcJunction(d));

        readjustCost(d, enJunc);
        lastDirection = enJunc.getDirection();
    }

    public void replaceTemplate(Design d, EnterWireJunction enter, ExitWireJunction exit, RouteTemplate replacement) {
        ArrayList<WireJunction> newTemplate = new ArrayList<>();
        int startIndex = 0;
        int endIndex = 0;
        for (int i = 0; i < template.size(); i++) {
            if (enter.equals(template.get(i)))
                startIndex = i;
            else if (exit.equals(template.get(i)))
                endIndex = i;
        }

        for (int i = 0; i < startIndex; i++) {
            newTemplate.add(template.get(i));
        }
        for (WireJunction junction : replacement.getTemplate()) {
            newTemplate.add(junction);
        }
        for (int i = endIndex + 1; i < template.size(); i++) {
            newTemplate.add(template.get(i));
        }

        template = newTemplate;

        readjustCost(d);
    }

    @Override
    public String toString() {
        String repr = "";
        for (int i = 0; i < template.size() - 1; i++)
            repr += template.get(i).toString() + " --> ";
        repr += template.get(template.size() - 1);
        return repr;
    }

    public Set<String> getUsage() {
        Set<String> usage = new HashSet<>();
        for (WireJunction junction : template)
            usage.add(junction.getNodeName());
        return usage;
    }

    public String hopSummary() {
        String repr = "<";
        for (int i = 1; i < template.size() - 3; i += 2) {
            repr += RouteUtil.directionToString(template.get(i).getDirection());
            repr += template.get(i).getWireLength();
            repr += " ";
        }
        repr += RouteUtil.directionToString(template.get(template.size() - 2).getDirection());
        repr += template.get(template.size() - 2).getWireLength();
        repr += ">[" + adjustedCost + "]";
        return repr;
    }
}