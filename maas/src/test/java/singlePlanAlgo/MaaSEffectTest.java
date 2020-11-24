package singlePlanAlgo;

import static org.junit.jupiter.api.Assertions.fail;

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
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.TypicalDurationScoreComputation;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.xml.sax.SAXException;

import com.google.common.collect.Maps;

import maasPackagesV2.MaaSPackages;
import maasPackagesV2.MaaSPackagesReader;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import matsimIntegrate.DynamicRoutingModuleWithMaas;
import optimizerAgent.MaaSOperator;
import optimizerAgent.MaaSOperatorOptimizationModule;
import optimizerAgent.MaaSUtil;
import running.RunUtils;

/**
 * This comparison evaluates the effect with and without the MaaS package
 * @author cetest
 *
 */
public class MaaSEffectTest {
	String PersonChangeWithCar_NAME = "person_TCSwithCar";
	String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
	
	String PersonFixed_NAME = "trip_TCS";
	String GVChange_NAME = "person_GV";
	String GVFixed_NAME = "trip_GV";
	
	private void setupNetworkTransitAndSignals(Config config) {
		//config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		//config.plans().setInputPersonAttributeFile("new Data/core/personAttributesHKI.xml");
		config.plans().setInputFile("new Data/core/50.plans.xml.gz");
		config.network().setInputFile("new Data/cal/output_network.xml.gz");
		config.vehicles().setVehiclesFile("new Data/core/VehiclesHKI.xml");
		
		config.network().setLaneDefinitionsFile("new Data/cal/output_lanes.xml"); //Lanes
		ConfigGroup sscg = config.getModule("signalsystems"); //Signals
		sscg.addParam("signalcontrol", "new Data/cal/output_signal_control_v2.0.xml");
		sscg.addParam("signalgroups", "new Data/cal/output_signal_groups_v2.0.xml");
		sscg.addParam("signalsystems", "new Data/cal/output_signal_systems_v2.0.xml");
		config.transit().setTransitScheduleFile("new Data/cal/output_transitSchedule.xml.gz");
		config.transit().setVehiclesFile("new Data/cal/output_transitVehicles.xml.gz");
		
		config.qsim().setFlowCapFactor(0.14);
		config.qsim().setStorageCapFactor(0.17);
		
		TransitRouterFareDynamicImpl.aStarSetting='c';
		TransitRouterFareDynamicImpl.distanceFactor=.01;
	}
	
	private void setupStrategies(Config config) {
		RunUtils.createStrategies(config, PersonChangeWithCar_NAME, 0.015, 0.01, 0.005, 0);
		RunUtils.createStrategies(config, PersonChangeWithoutCar_NAME, 0.015, 0.01, 0.005, 0);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), PersonChangeWithCar_NAME, 
				0.02, 100);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithCar_NAME, 
				0.01, 100);
		
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), PersonChangeWithoutCar_NAME, 
				0.02, 100);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithoutCar_NAME, 
				0.01, 100);
		
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), PersonFixed_NAME, 
				0.02, 125);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), GVFixed_NAME, 
				0.02, 125);
		RunUtils.createStrategies(config, PersonFixed_NAME, 0.02, 0.01, 0, 40);
		RunUtils.createStrategies(config, GVChange_NAME, 0.02, 0.005, 0, 0);
		RunUtils.createStrategies(config, GVFixed_NAME, 0.02, 0.005, 0, 40);
		
		config.strategy().addStrategySettings(createStrategySettings(MaaSPlanStrategy.class.getName(),.05,200,PersonChangeWithCar_NAME));
		config.strategy().addStrategySettings(createStrategySettings(MaaSPlanStrategy.class.getName(),.05,200,PersonChangeWithoutCar_NAME));
		config.strategy().addStrategySettings(createStrategySettings(MaaSPlanStrategy.class.getName(),.05,200,PersonFixed_NAME));
		
		config.strategy().setFractionOfIterationsToDisableInnovation(0.85);
	}
	
	@Test
	void evaluateMaaSPackageEffect() {		
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "new Data/data/config_clean.xml");
		
		Config configGV = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(configGV, "new Data/data/config_Ashraf.xml");
		for (ActivityParams act: configGV.planCalcScore().getActivityParams()) {
			if(act.getActivityType().contains("Usual place of work")) {
				act.setMinimalDuration(3600);
			}
			if(config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getActivityParams(act.getActivityType())==null) {
				config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(PersonFixed_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(GVChange_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(GVFixed_NAME).addActivityParams(act);
			}
		}
		
		setupNetworkTransitAndSignals(config);
		setupStrategies(config);
		
		config.controler().setOutputDirectory("toyScenarioLarge/output_WithMaaSwithoutOptim_IntelligentAgent4");
		config.controler().setLastIteration(250);
		config.parallelEventHandling().setNumberOfThreads(7);
		config.controler().setWritePlansInterval(10);
		config.controler().setWriteEventsInterval(20);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		config.global().setNumberOfThreads(23);
		config.planCalcScore().setWriteExperiencedPlans(false);
		config.removeModule("emissions");
		config.removeModule("roadpricing");
		
		new ConfigWriter(config).write("test/config.xml");
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		ObjectAttributes obj = new ObjectAttributes();
		new ObjectAttributesXmlReader(obj).readFile("new Data/core/personAttributesHKI.xml");
		
		for(Person person: scenario.getPopulation().getPersons().values()) { //Setup the subpopulation and vehicle attributes
			String subPop = (String) obj.getAttribute(person.getId().toString(), "SUBPOP_ATTRIB_NAME");
			PopulationUtils.putSubpopulation(person, subPop); 
			Id<Vehicle> vehId = Id.create(person.getId().toString(), Vehicle.class);
			Map<String, Id<Vehicle>> modeToVehicle = Maps.newHashMap();
			modeToVehicle.put("taxi", vehId);
			modeToVehicle.put("car", vehId);
			VehicleUtils.insertVehicleIdsIntoAttributes(person, modeToVehicle);
			for(PlanElement pe :person.getSelectedPlan().getPlanElements()) { //Add the scoring parameters for every activity
				if (pe instanceof Activity) {
					Activity act = (Activity) pe;
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
		SmallExample.additionalSettingsForMaaS(scenario, PersonChangeWithCar_NAME, "test/packages_July2020_20.xml");
		RunUtils.scaleDownPt(scenario.getTransitVehicles(), .1);
		
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new MaaSDataLoaderV2(MaaSDataLoaderV2.typeOperator));
		controler.addOverridingModule(new MaaSOperatorOptimizationModule());
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser;
		
		try {
			saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse("new Data/data/busFare.xml", busFareGetter);
			controler.addOverridingModule(new DynamicRoutingModuleWithMaas(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
					"fare/transitDiscount.json", "fare/light_rail_fares.csv", "fare/busFareGTFS.json", "fare/ferryFareGTFS.json"));
			
			//controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
			//		"fare/GMB.csv", "fare/light_rail_fares.csv"));
			
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
