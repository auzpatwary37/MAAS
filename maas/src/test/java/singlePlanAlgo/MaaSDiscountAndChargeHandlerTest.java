package singlePlanAlgo;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.common.util.LoggerUtils;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;
import org.xml.sax.SAXException;

import com.google.common.collect.Maps;

import MaaSPackages.MaaSPackages;
import MaaSPackages.MaaSPackagesReader;
import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import optimizerAgent.MaaSOperator;
import optimizerAgent.MaaSOperatorOptimizationModule;
import optimizerAgent.MaaSOperatorStrategy;
import optimizerAgent.MaaSUtil;
import optimizerAgent.MetamodelModule;

/**
 * This class will basically test the connection between different components for MaaS implementation in MATSim.
 * This however, will not test the optimization part of MaaS
 * @author ashraf
 *
 */
class MaaSDiscountAndChargeHandlerTest {

	@Test
	void test() {
		// This will test the optimization agent insertion
		
				Config config = RunUtils.provideConfig();
				
				config.addModule(new MaaSConfigGroup());
				
				config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packages_May2020.xml");
				LoggerUtils.setVerbose(false);
				
				config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
				
				//Add the MaaS package choice strategy
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
				
				for(Person person: scenario.getPopulation().getPersons().values()) {
					Id<Vehicle> vehId = Id.create(person.getId().toString(), Vehicle.class);
					Map<String, Id<Vehicle>> modeToVehicle = Maps.newHashMap();
					modeToVehicle.put("taxi", vehId);
					modeToVehicle.put("car", vehId);
					VehicleUtils.insertVehicleIdsIntoAttributes(person, modeToVehicle);
				}
				
				MaaSPackages packages = new MaaSPackagesReader().readPackagesFile(scenario.getConfig().getModules().get(MaaSConfigGroup.GROUP_NAME).getParams().get(MaaSConfigGroup.INPUT_FILE)); //It has to be consistent with the config.
				
				Activity act = MaaSUtil.createMaaSOperator(packages, scenario.getPopulation(), "test/agentPop.xml",new Tuple<>(.5,2.));
				
				ActivityParams param = new ActivityParams(act.getType());
				param.setTypicalDuration(20*3600);
				param.setMinimalDuration(8*3600);
				param.setScoringThisActivityAtAll(false);			
				scenario.getConfig().planCalcScore().getScoringParameters(MaaSUtil.MaaSOperatorAgentSubPopulationName).addActivityParams(param);
				
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
				controler.addOverridingModule(new MaaSDataLoader());
				controler.addOverridingModule(new MetamodelModule());
				controler.addOverridingModule(new MaaSOperatorOptimizationModule());
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
