package optimizerAgent;

import static org.junit.Assert.*;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;
import org.xml.sax.SAXException;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;

import singlePlanAlgo.MaaSConfigGroup;
import singlePlanAlgo.MaaSDataLoaderV2;
import singlePlanAlgo.MaaSPlanStrategy;
import singlePlanAlgo.RunUtils;

public class PlanTranslatorTest {

	@Test
	public void test() {
		//fail("Not yet implemented");
				
				Config config = RunUtils.provideConfig();
				
				StrategySettings stratSets = new StrategySettings();
				
				stratSets.setStrategyName(MaaSPlanStrategy.class.getName());
				stratSets.setWeight(0.7);
				stratSets.setDisableAfter(200);
				stratSets.setSubpopulation("person_TCSwithCar");
				config.strategy().addStrategySettings(stratSets);
				config.addModule(new MaaSConfigGroup());
				config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packages.xml");
				
				Scenario scenario = ScenarioUtils.loadScenario(config);
				
				for(LanesToLinkAssignment l2l:scenario.getLanes().getLanesToLinkAssignments().values()) {
					for(Lane l: l2l.getLanes().values()) {
						
						//Why this is done? 
						//Why .1 specifically??
						l.setCapacityVehiclesPerHour(1800*.15);
					}
				}
				
				
//				for(Person p:scenario.getPopulation().getPersons().values()) {
//					VehicleUtils.insertVehicleIdIntoAttributes(p, "car", Id.createVehicleId(p.getId().toString()));
//				}
				
				scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
				Controler controler = new Controler(scenario);
				controler.addOverridingModule(new MaaSDataLoaderV2(MaaSDataLoaderV2.typeOperator));
				ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
				SAXParser saxParser;
				
				try {
					saxParser = SAXParserFactory.newInstance().newSAXParser();
					saxParser.parse("new Data/data/busFare.xml", busFareGetter);
					controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
							"fare/transitDiscount.json", "fare/light_rail_fares.csv", "fare/busFareGTFS.json", "fare/ferryFareGTFS.json"));
					
//					controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
//							"fare/GMB.csv", "fare/light_rail_fares.csv"));
					
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
				Signals.configure(controler);
				
				controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
				controler.run();
				fail("Not yet implemented");
	}

}
