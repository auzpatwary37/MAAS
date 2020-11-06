package offlineAnalysis;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;



import createPTGTFS.FareCalculatorPTGTFS;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import dynamicTransitRouter.transfer.AllPTTransferDiscount;
import optimizerAgent.MaaSUtil;
import transitCalculatorsWithFare.TransitFareHandler;

public class PersonFareAnalysis {
	private final static String dir = "/disk/r067/eleead/workspace/matsim-playground/analysis/";
	private final static String configFile = dir + "output_config.xml";
	
	private static final String eventsFile = dir + "platformOptimized/output_events.xml.gz";

	public static void main(String[] args) throws IOException, Exception {
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, configFile);
		config.plans().setInputFile(dir+"platformOptimized/output_plans.xml.gz");

		Scenario scenario = ScenarioUtils.loadScenario(config);
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		saxParser.parse("fare/busFare.xml", busFareGetter);
		
		Map<String, FareCalculator> fareCalculator = new HashMap<>();
		fareCalculator.put("bus", FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/busFareGTFS.json"));
		fareCalculator.put("minibus", busFareGetter.get()); 
		fareCalculator.put("train", new MTRFareCalculator("fare/mtr_lines_fares.csv", scenario.getTransitSchedule()));

		// The tram fare calculator
		fareCalculator.put("tram",new UniformFareCalculator(2.6));
		fareCalculator.put("ferry",FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/ferryFareGTFS.json"));
		
		AllPTTransferDiscount discount = new AllPTTransferDiscount("fare/transitDiscount.json");
		EventsManager eventsManager = EventsUtils.createEventsManager();
		TransitFareHandler tfh = new TransitFareHandler(eventsManager, fareCalculator, 
				scenario.getTransitSchedule().getTransitLines(), discount);
		eventsManager.addHandler(tfh);

		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
		matsimEventsReader.readFile(eventsFile);
		
		Map<Id<Person>, Double> busFareSaved = tfh.getPersonBusFareCollected();
		Map<Id<Person>, Double> mtrFareSaved = tfh.getPersonMTRFareCollected();
		Map<Id<Person>, Double> otherFareSaved = tfh.getPersonOtherFareCollected();
		
		FileWriter fileWriter = new FileWriter("output/fare_analysisOptimized.csv");
		fileWriter.write("person_id,plan,busFareSaved,mtrFareSaved,otherFareSaved\n");
		for(Person p: scenario.getPopulation().getPersons().values()) {
			String packageName = (String) p.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
			StringBuilder sb = new StringBuilder();
			sb.append(p.getId().toString()+","+packageName+",");
			sb.append(busFareSaved.get(p.getId())+","+mtrFareSaved.get(p.getId())+","+otherFareSaved.get(p.getId())+'\n');
			fileWriter.write(sb.toString());
		}
		fileWriter.close();
		
		//TODO: Find out the actual best plans
	}
}
