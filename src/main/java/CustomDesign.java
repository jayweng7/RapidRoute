import com.xilinx.rapidwright.design.Design;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class CustomDesign {

    /*
     * Top level class which does everything
     */

    public static final String EXAMPLE_PLACEMENTS_FILE_NAME = ResourcesManager.RESOURCES_DIR
            + "register_pair_example.conf";
    public static final String EXAMPLE_ROUTES_FILE_NAME = ResourcesManager.RESOURCES_DIR
            + "routes_example.conf";

    private static void printUsage(OptionParser parser) throws IOException {
        System.out.println("java CustomDesign [-h] [-v] [--example] [--name DESIGN_NAME] [--out OUT_FILE_NAME]\n");
        System.out.println("  Create and route a design based on placements.conf and routes.conf. Uses routes_example.conf instead if --example is specified.\n");
        parser.printHelpOn(System.out);
    }

    private static OptionParser createOptionParser() {
        OptionParser p = new OptionParser();
        p.accepts("name").withOptionalArg().defaultsTo("custom_design").describedAs("Name of design");
        p.accepts("out").withOptionalArg().defaultsTo("custom_design").describedAs("Prefix for output *_unrouted.dcp, *_custom_routed.dcp");
        p.accepts("jobs").withOptionalArg().defaultsTo("4").describedAs("Number of concurrent jobs for routing");
        p.accepts("help").forHelp();
        p.accepts("verbose");
        p.accepts("example");
        return p;
    }

    public static void main(String[] args) throws IOException {
        OptionParser parser = createOptionParser();
        OptionSet options = parser.parse(args);

        RouterLog.Level logLevel = (options.has("verbose")) ? RouterLog.Level.VERBOSE : RouterLog.Level.INFO;
        RouterLog.init(logLevel);

        if (options.has("help")) {
            printUsage(parser);
            return;
        }

        if (options.has("example")) {
            ResourcesManager.COMPONENTS_FILE_NAME = ComplexRegister.EXAMPLE_COMPONENTS_FILE_NAME;
            ResourcesManager.PLACEMENTS_FILE_NAME = EXAMPLE_PLACEMENTS_FILE_NAME;
            ResourcesManager.ROUTES_FILE_NAME = EXAMPLE_ROUTES_FILE_NAME;
        }

        ResourcesManager.initConfigs();

        Design d = ResourcesManager.newDesignFromSources((String) options.valueOf("name"));

        HashMap<String, ComplexRegister> registers = ResourcesManager.registersFromPlacements(d);
        ArrayList<RegisterConnection> connections = ResourcesManager.connectionsFromRoutes(d, registers);

        // Placement
        DesignPlacer.initializePlacer(d);
        DesignPlacer.createTopLevelClk();
        for (ComplexRegister register : registers.values()) {
            DesignPlacer.prepareNewRegisterForPlacement(register);
        }

        DesignPlacer.place();


        // Routing
        DesignRouter.initializeRouter(d, Integer.valueOf((String) options.valueOf("jobs")));
        for (RegisterConnection connection : connections) {
            DesignRouter.prepareNewConnectionForRouting(d, connection);
        }
        DesignRouter.createNetsForConnections();

        d.writeCheckpoint(ResourcesManager.OUTPUT_DIR + options.valueOf("out") + "_unrouted.dcp");

        DesignRouter.routeDesign();

        d.writeCheckpoint(ResourcesManager.OUTPUT_DIR + options.valueOf("out") + "_custom_routed.dcp");

    }
}
