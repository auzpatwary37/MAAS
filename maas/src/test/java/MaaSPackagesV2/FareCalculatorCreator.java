package MaaSPackagesV2;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.xml.sax.SAXException;

import createPTGTFS.FareCalculatorPTGTFS;
import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.LRFareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import optimizerAgent.transitGenerator;
import singlePlanAlgo.RunUtils;

public class FareCalculatorCreator {
	
	public static Map<String,FareCalculator> getHKFareCalculators(){
		Map<String,FareCalculator> fareCalculators = new HashMap<>();
		Config config =RunUtils.provideConfig();
		config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		TransitSchedule ts = ScenarioUtils.loadScenario(config).getTransitSchedule();
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(ts);
		SAXParser saxParser;
		
		try {
			saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse("new Data/data/busFare.xml", busFareGetter);
			fareCalculators.put("train",new MTRFareCalculator("fare/mtr_lines_fares.csv", ts));
			fareCalculators.put("minibus",busFareGetter.get());
			fareCalculators.put("tram",new UniformFareCalculator(2.6));
			fareCalculators.put("LR",new LRFareCalculator("fare/light_rail_fares.csv"));
			FareCalculator busFareCal = FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/busFareGTFS.json");
			FareCalculator ferryFareCal = FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/ferryFareGTFS.json");
			fareCalculators.put("bus",busFareCal);
			fareCalculators.put("ferry",ferryFareCal);
			
		} catch (ParserConfigurationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return fareCalculators;
	}

	public static Map<String,FareCalculator> getToyScenarioFareCalculators(){
		Map<String,FareCalculator> fareCalculators = new HashMap<>();
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "src/main/resources/toyScenarioData/config.xml");
		config.network().setInputFile("src/main/resources/toyScenarioData/network.xml");
		config.vehicles().setVehiclesFile("src/main/resources/toyScenarioData/vehicles.xml");
		config.transit().setTransitScheduleFile("src/main/resources/toyScenarioData/transitSchedule.xml");
		config.transit().setVehiclesFile("src/main/resources/toyScenarioData/transitVehicles.xml");
		//config.plans().setInputFile("src/main/resources/output_plans.xml.gz");
		
		config.scenario().setSimulationPeriodInDays(1.0);
		Scenario scenario;
		scenario = ScenarioUtils.loadScenario(config);
		try {
			fareCalculators.put("bus",transitGenerator.createBusFareCalculator(scenario.getTransitSchedule(), Arrays.asList("src/main/resources/toyScenarioData/Bus_1_fare_Test.csv","src/main/resources/toyScenarioData/Bus_2_fare_Test.csv")));
			fareCalculators.put("train",new MTRFareCalculator("src/main/resources/toyScenarioData/Mtr_fare.csv", scenario.getTransitSchedule()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return fareCalculators;
	}
}
