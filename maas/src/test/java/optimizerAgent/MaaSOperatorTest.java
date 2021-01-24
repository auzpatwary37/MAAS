package optimizerAgent;

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
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;
import org.xml.sax.SAXException;

import maasPackagesV2.MaaSPackages;
import maasPackagesV2.MaaSPackagesReader;
import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;

import singlePlanAlgo.MaaSConfigGroup;
import singlePlanAlgo.MaaSDataLoaderV2;
import singlePlanAlgo.MaaSPlanStrategy;
import singlePlanAlgo.RunUtils;

public class MaaSOperatorTest {

	@Test
	public void test() {
		// This will test the optimization agent insertion
		
		Config config = RunUtils.provideConfig();
		
		config.addModule(new MaaSConfigGroup());
		
		config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packages.xml");
		
		
		
		
		StrategySettings stratSets = new StrategySettings();
		stratSets.setStrategyName(MaaSPlanStrategy.class.getName());
		stratSets.setWeight(0.7);
		stratSets.setDisableAfter(200);
		stratSets.setSubpopulation("person_TCSwithCar");
		config.strategy().addStrategySettings(stratSets);
		
		stratSets = new StrategySettings();
		stratSets.setStrategyName(MaaSOperatorStrategy.class.getName());
		stratSets.setWeight(1);
		stratSets.setDisableAfter(200);
		stratSets.setSubpopulation(MaaSOperator.type);
		config.strategy().addStrategySettings(stratSets);
		
		
		ScoringParameterSet s = config.planCalcScore().getOrCreateScoringParameters(MaaSOperator.type);
		
		ScoringParameterSet ss =  config.planCalcScore().getScoringParameters("person_TCSwithCar");
		
		
		
		s.getOrCreateModeParams("car").setMarginalUtilityOfTraveling(ss.getOrCreateModeParams("car").getMarginalUtilityOfTraveling());
		s.getOrCreateModeParams("car").setMarginalUtilityOfDistance(ss.getOrCreateModeParams("car").getMarginalUtilityOfDistance());
		s.setMarginalUtilityOfMoney(ss.getMarginalUtilityOfMoney());
		s.getOrCreateModeParams("car").setMonetaryDistanceRate(ss.getOrCreateModeParams("car").getMonetaryDistanceRate());
		s.getOrCreateModeParams("walk").setMarginalUtilityOfTraveling(ss.getOrCreateModeParams("walk").getMarginalUtilityOfTraveling());
		s.getOrCreateModeParams("walk").setMonetaryDistanceRate(ss.getOrCreateModeParams("walk").getMarginalUtilityOfDistance());
		s.setPerforming_utils_hr(ss.getPerforming_utils_hr());
		s.setMarginalUtlOfWaitingPt_utils_hr(ss.getMarginalUtlOfWaitingPt_utils_hr());
		
		new ConfigWriter(config).write("test/config.xml");
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		MaaSPackages packages = new MaaSPackagesReader().readPackagesFile("test/packages.xml");
		
		MaaSUtil.createMaaSOperator(packages, scenario.getPopulation(), "test/agentPop.xml",new Tuple<>(.5,2.),null,null,true);
		
		
		for(LanesToLinkAssignment l2l:scenario.getLanes().getLanesToLinkAssignments().values()) {
			for(Lane l: l2l.getLanes().values()) {
				
				//Why this is done? 
				//Why .1 specifically??
				l.setCapacityVehiclesPerHour(1800*.15);
			}
		}
		
		
//		for(Person p:scenario.getPopulation().getPersons().values()) {
//			VehicleUtils.insertVehicleIdIntoAttributes(p, "car", Id.createVehicleId(p.getId().toString()));
//		}
		
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new MaaSDataLoaderV2(MaaSDataLoaderV2.typeOperator));
		//controler.addOverridingModule(new MetamodelModule());
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser;
		
		try {
			saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse("new Data/data/busFare.xml", busFareGetter);
			controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
					"fare/transitDiscount.json", "fare/light_rail_fares.csv", "fare/busFareGTFS.json", "fare/ferryFareGTFS.json"));
			
//			controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
//					"fare/GMB.csv", "fare/light_rail_fares.csv"));
			
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
		
		
		
		
		//fail("Not yet implemented");
	}

}
