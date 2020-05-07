package optimizerAgent;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicles;

public class asdf {
public static void main(String[] args) {
		
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile("toyScenario/toyScenarioData/network.xml");
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		Tuple<TransitSchedule,Vehicles> ts = transitGenerator.createTransit(scenario, scenario.getNetwork());
		new TransitScheduleWriter(ts.getFirst()).writeFileV2("toyScenario/toyScenarioData/transitSchedule.xml");
		new MatsimVehicleWriter(ts.getSecond()).writeFile("toyScenario/toyScenarioData/transitVehicles.xml");
	}
}
