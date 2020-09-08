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
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.TypicalDurationScoreComputation;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.replanning.strategies.KeepLastSelected;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.xml.sax.SAXException;

import com.google.common.collect.Maps;

import MaaSPackages.FareCalculatorCreator;
import MaaSPackages.MaaSPackages;
import MaaSPackages.MaaSPackagesReader;
import MaaSPackages.MaaSPackagesWriter;
import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import matsimIntegrate.DynamicRoutingModuleWithMaas;
import optimizerAgent.MaaSOperator;
import optimizerAgent.MaaSOperatorOptimizationModule;
import optimizerAgent.MaaSOperatorStrategy;
import optimizerAgent.MaaSUtil;
import running.RunUtils;

/**
 * This class will basically test the connection between different components for MaaS implementation in MATSim.
 * This however, will not test the optimization part of MaaS
 * @author ashraf
 *
 */
class MaaSDiscountAndChargeHandlerTest {


	@Test
	void test() {
		
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
		
		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";
		
		// This will test the optimization agent insertion
				Config config = singlePlanAlgo.RunUtils.provideConfig();
				new ConfigWriter(config).write("RunUtilsConfig.xml");
				//OutputDirectoryLogging.catchLogEntries();
				config.addModule(new MaaSConfigGroup());
				config.controler().setLastIteration(250);
				config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packages_all.xml");
				//config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"packages_July2020_400.xml");
				
				config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
				config.plans().setInputPersonAttributeFile("new Data/core/personAttributesHKI.xml");
//				config.plans().setInputFile("new Data/core/20.plans.xml.gz");
				config.controler().setOutputDirectory("toyScenarioLarge/output_optim_stable_1");
				config.controler().setWritePlansInterval(10);
				
//				
				RunUtils.createStrategies(config, PersonChangeWithCar_NAME, 0.015, 0.01, 0.005, 0);
				RunUtils.createStrategies(config, PersonChangeWithoutCar_NAME, 0.015, 0.01, 0.005, 0);
				RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), PersonChangeWithCar_NAME, 
						0.05, 100);
				RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithCar_NAME, 
						0.01, 100);
				
				RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), PersonChangeWithoutCar_NAME, 
						0.05, 100);
				RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithoutCar_NAME, 
						0.01, 100);
				
				RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), PersonFixed_NAME, 
						0.02, 125);
				RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), GVFixed_NAME, 
						0.02, 125);
				
				RunUtils.createStrategies(config, PersonFixed_NAME, 0.02, 0.01, 0, 40);
				RunUtils.createStrategies(config, GVChange_NAME, 0.02, 0.005, 0, 0);
				RunUtils.createStrategies(config, GVFixed_NAME, 0.02, 0.005, 0, 40);
				
				//Add the MaaS package choice strategy
				config.strategy().addStrategySettings(createStrategySettings(MaaSPlanStrategy.class.getName(),.05,200,"person_TCSwithCar"));
				config.strategy().addStrategySettings(createStrategySettings(MaaSPlanStrategy.class.getName(),.05,200,"person_TCSwithoutCar"));
				config.strategy().addStrategySettings(createStrategySettings(MaaSPlanStrategy.class.getName(),.05,200,"trip_TCS"));
				config.strategy().addStrategySettings(createStrategySettings(MaaSOperatorStrategy.class.getName(),1,200,MaaSUtil.MaaSOperatorAgentSubPopulationName));
				
				
				//___________________
				
//				RunUtils.addStrategy(config, "KeepLastSelected", MaaSUtil.MaaSOperatorAgentSubPopulationName, 
//						1, 400);
				
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
				
				
				ObjectAttributes obj = new ObjectAttributes();
				new ObjectAttributesXmlReader(obj).readFile("new Data/core/personAttributesHKI.xml");
				
				for(Person person: scenario.getPopulation().getPersons().values()) {
					String subPop = (String) obj.getAttribute(person.getId().toString(), "SUBPOP_ATTRIB_NAME");
					PopulationUtils.putSubpopulation(person, subPop);
					Id<Vehicle> vehId = Id.create(person.getId().toString(), Vehicle.class);
					Map<String, Id<Vehicle>> modeToVehicle = Maps.newHashMap();
					modeToVehicle.put("taxi", vehId);
					modeToVehicle.put("car", vehId);
					VehicleUtils.insertVehicleIdsIntoAttributes(person, modeToVehicle);
					for(PlanElement pe :person.getSelectedPlan().getPlanElements()) {
						if (pe instanceof Activity) {
							Activity act = (Activity)pe;
							
							if(scenario.getConfig().planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getActivityParams(act.getType()) == null) {
								ActivityParams params = new ActivityParams(act.getType());
								params.setTypicalDurationScoreComputation(TypicalDurationScoreComputation.uniform);
								params.setMinimalDuration(3600);
								params.setTypicalDuration(8*3600);
								config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).addActivityParams(params);
								config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).addActivityParams(params);
								config.planCalcScore().getScoringParameters(PersonFixed_NAME).addActivityParams(params);
								config.planCalcScore().getScoringParameters(GVChange_NAME).addActivityParams(params);
								config.planCalcScore().getScoringParameters(GVFixed_NAME).addActivityParams(params);
							}
						}
					}
				}
				
//				for(Link link:scenario.getNetwork().getLinks().values()) {
//					link.setCapacity(link.getCapacity()*.14);
//				}
				
				MaaSPackages packages = new MaaSPackagesReader().readPackagesFile(scenario.getConfig().getModules().get(MaaSConfigGroup.GROUP_NAME).getParams().get(MaaSConfigGroup.INPUT_FILE)); //It has to be consistent with the config.
				//RunUtils.scaleDownPopulation(scenario.getPopulation(), 0.1);
				Activity act = MaaSUtil.createMaaSOperator(packages, scenario.getPopulation(), "test/agentPop.xml",new Tuple<>(.5,2.5));
				
				ActivityParams param = new ActivityParams(act.getType());
				param.setTypicalDuration(20*3600);
				param.setMinimalDuration(8*3600);
				param.setScoringThisActivityAtAll(false);			
				scenario.getConfig().planCalcScore().getScoringParameters(MaaSUtil.MaaSOperatorAgentSubPopulationName).addActivityParams(param);
				
//				for(LanesToLinkAssignment l2l:scenario.getLanes().getLanesToLinkAssignments().values()) {
//					for(Lane l: l2l.getLanes().values()) {
//						
//						//Why this is done? 
//						//Why .1 specifically??
//						l.setCapacityVehiclesPerHour(1800*.14);
//					}
//				}
				
				
				for(VehicleType vt:scenario.getTransitVehicles().getVehicleTypes().values()) {
					vt.setPcuEquivalents(vt.getPcuEquivalents()*.14);
					VehicleCapacity vc = vt.getCapacity();
					vc.setSeats(Math.max((int)(vc.getSeats()*.1),1));
					vc.setStandingRoom(Math.max((int)(vc.getStandingRoom()*.1),1));
				}
				
//				for(Person p:scenario.getPopulation().getPersons().values()) {
//					VehicleUtils.insertVehicleIdIntoAttributes(p, "car", Id.createVehicleId(p.getId().toString()));
//				}
				
				scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
				Controler controler = new Controler(scenario);
				controler.addOverridingModule(new MaaSDataLoader());
				controler.addOverridingModule(new MaaSOperatorOptimizationModule());
				ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
				SAXParser saxParser;
				
				try {
					saxParser = SAXParserFactory.newInstance().newSAXParser();
					saxParser.parse("new Data/data/busFare.xml", busFareGetter);
					controler.addOverridingModule(new DynamicRoutingModuleWithMaas(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
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

	
	public static StrategySettings createStrategySettings(String name,double weight,int disableAfter,String subPop) {
		StrategySettings stratSets = new StrategySettings();
		stratSets.setStrategyName(name);
		stratSets.setWeight(weight);
		stratSets.setDisableAfter(disableAfter);
		stratSets.setSubpopulation(subPop);
		return stratSets;
	}
}
