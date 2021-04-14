package elasticDemand;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.TypicalDurationScoreComputation;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;
import org.xml.sax.SAXException;

import com.google.common.collect.Maps;


import clustering.RandomCluster;

import createPTGTFS.FareCalculatorPTGTFS;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.LRFareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import maasPackagesV2.MaaSPackages;
import maasPackagesV2.MaaSPackagesReader;
import optimizer.Adam;
import optimizer.Optimizer;
import optimizer.ScaledAdam;
import optimizerAgent.IntelligentOperatorDecisionEngineV2;
import optimizerAgent.MaaSOperator;
import optimizerAgent.MaaSUtil;
import optimizerAgent.ObjectiveAndGradientCalculator;
import optimizerAgent.PersonPlanSueModel;
import optimizerAgent.VariableDetails;
import optimizerAgent.packUsageStat;
import optimizerAgent.timeBeansWrapper;
import running.RunUtils;
import singlePlanAlgo.MaaSConfigGroup;
import singlePlanAlgo.MaaSDataLoaderV2;
import singlePlanAlgo.MaaSPlanStrategy;
import singlePlanAlgo.UnnecessaryMaaSPlanRemovalStrategy;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.calibrator.ObjectiveCalculator;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SignalFlowReductionGenerator;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class MetaModelRunWithElasticDemand {
	public static final Logger logger = Logger.getLogger(MetaModelRunWithElasticDemand.class);
	public static void main(String[] args) throws IOException {
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
		
		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";
		String operatorID = "Govt";
		boolean optimizeForBreakEven = true;
		Double initialTotalSystemTravelTimeInMoney = null;
		
		MaaSPackages pac = new MaaSPackagesReader().readPackagesFile("test/packages_July2020_20.xml");
		MaaSPackages pacAll = MaaSUtil.createUnifiedMaaSPackages(pac, "Govt", "allPack");
		pac = null;
		pacAll.setAllOPeratorReimbursementRatio(0.9);
		Config config = singlePlanAlgo.RunUtils.provideConfig();
		config.plans().setInputFile("test/refinedPop_13Apr.xml");
		new ConfigWriter(config).write("RunUtilsConfig.xml");
		//OutputDirectoryLogging.catchLogEntries();
		config.addModule(new MaaSConfigGroup());
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(250);
		
		config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packages_"+operatorID+".xml");
		//new MaaSPackagesWriter(pac).write("test/packages_"+operatorID+".xml");
		
		//config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packages_"+operatorID+".xml");
		
		config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		config.plans().setInputPersonAttributeFile("new Data/core/personAttributesHKI.xml");
		//config.plans().setInputFile("toyScenarioLarge/output_optim_operatorPlatformGovt/output_plans.xml.gz");
		config.controler().setOutputDirectory("toyScenarioLarge/output_optim_operatorPlatform2"+operatorID);
		config.controler().setWritePlansInterval(50);
		config.planCalcScore().setWriteExperiencedPlans(true);
//		
		RunUtils.createStrategies(config, PersonChangeWithCar_NAME, 0.015, 0.015, 0.01, 0);
		RunUtils.createStrategies(config, PersonChangeWithoutCar_NAME, 0.015, 0.015, 0.01, 0);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), PersonChangeWithCar_NAME, 
				0.05, 100);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithCar_NAME, 
				0.05, 220);
		
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), PersonChangeWithoutCar_NAME, 
				0.05, 100);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithoutCar_NAME, 
				0.05, 225);
		
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), PersonFixed_NAME, 
				0.02, 125);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), GVFixed_NAME, 
				0.02, 125); 
		RunUtils.createStrategies(config, PersonFixed_NAME, 0.02, 0.01, 0, 40);
		RunUtils.createStrategies(config, GVChange_NAME, 0.02, 0.005, 0, 0);
		RunUtils.createStrategies(config, GVFixed_NAME, 0.02, 0.005, 0, 40);
		
		//Add the MaaS package choice strategy
		config.strategy().addStrategySettings(createStrategySettings(MaaSPlanStrategy.class.getName(),.05,225,"person_TCSwithCar"));
		config.strategy().addStrategySettings(createStrategySettings(MaaSPlanStrategy.class.getName(),.05,225,"person_TCSwithoutCar"));
		config.strategy().addStrategySettings(createStrategySettings(MaaSPlanStrategy.class.getName(),.05,225,"trip_TCS"));
		//config.strategy().addStrategySettings(createStrategySettings(MaaSOperatorStrategy.class.getName(),1,200,MaaSUtil.MaaSOperatorAgentSubPopulationName));
		
		config.strategy().addStrategySettings(createStrategySettings(UnnecessaryMaaSPlanRemovalStrategy.class.getName(),.05,225,"person_TCSwithCar"));
		config.strategy().addStrategySettings(createStrategySettings(UnnecessaryMaaSPlanRemovalStrategy.class.getName(),.05,225,"person_TCSwithoutCar"));
		config.strategy().addStrategySettings(createStrategySettings(UnnecessaryMaaSPlanRemovalStrategy.class.getName(),.05,225,"trip_TCS"));
		
		config.strategy().addStrategySettings(createStrategySettings(ActivityAdditionStrategy.class.getName(),.05,180,"person_TCSwithCar"));
		config.strategy().addStrategySettings(createStrategySettings(ActivityAdditionStrategy.class.getName(),.05,180,"person_TCSwithoutCar"));
		
		//___________________
		
		RunUtils.addStrategy(config, "KeepLastSelected", MaaSUtil.MaaSOperatorAgentSubPopulationName, 
				1, 400);
		
		config.planCalcScore().setWriteExperiencedPlans(true);
		config.global().setNumberOfThreads(20);
		config.qsim().setNumberOfThreads(10);				
		
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
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
	Set<Set<Id<TransitLine>>> tlSets = new RandomCluster<Id<TransitLine>>().createRandomSplit(scenario.getTransitSchedule().getTransitLines().keySet(), 20);
//	Set<Set<Id<TransitLine>>> tlSets = new UsageBasedCluster<TransitLine>().createUsageBasedSplit(scenario.getTransitSchedule().getTransitLines(), 
//			ObjectiveAndGradientCalculator.readSimpleMap("test/ averageWaitTime.csv"), 20);
//	
	ObjectAttributes objATT = new ObjectAttributes();
	new ObjectAttributesXmlReader(objATT).readFile("new Data/core/personAttributesHKI.xml");
	
	
	
	
	for(Person person: scenario.getPopulation().getPersons().values()) {
		String subPop = (String) objATT.getAttribute(person.getId().toString(), "SUBPOP_ATTRIB_NAME");
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
	
	Map<String,Map<String,Double>> variables  = new HashMap<>();
	Map<String,Map<String,Tuple<Double,Double>>> variableLimits  = new HashMap<>();
	variables.put("Govt", new HashMap<>());
	variableLimits.put("Govt", new HashMap<>());
	Map<String,Map<String,VariableDetails>> operatorVariables = new HashMap<>(); 
	operatorVariables.put("Govt", new HashMap<>());
	Map<String,Optimizer> operatorOptimizers = new HashMap<>();
	String costKey = MaaSUtil.generateMaaSPackageCostKey("allPack");
	variables.get("Govt").put(costKey,20.);
	variableLimits.get("Govt").put(costKey,new Tuple<Double,Double>(0.01,50.));
	int i=0;
	Map<String,VariableDetails> Param = new HashMap<>();
	Param.put(costKey, new VariableDetails(costKey, variableLimits.get("Govt").get(costKey), variables.get("Govt").get(costKey)));
	operatorVariables.get("Govt").put(costKey, Param.get(costKey));
	for(Set<Id<TransitLine>>sss:tlSets){
		String key  = MaaSUtil.generateMaaSTransitLinesDiscountKey("allPack", sss,"tl"+i);
		variables.get("Govt").put(key,0.8);
		variableLimits.get("Govt").put(key,new Tuple<Double,Double>(0.01,1.));
		Param.put(key, new VariableDetails(key, variableLimits.get("Govt").get(key), variables.get("Govt").get(key)));
		operatorVariables.get("Govt").put(key, Param.get(key));
		i++;
	};
	
	Map<String,FareCalculator>fareCalculators = new HashMap<>();
	SAXParser saxParser;
	ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
	try {
		saxParser = SAXParserFactory.newInstance().newSAXParser();
		saxParser.parse("new Data/data/busFare.xml", busFareGetter);
		
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
	
	try {
		fareCalculators.put("train", new MTRFareCalculator("fare/mtr_lines_fares.csv",scenario.getTransitSchedule()));
		fareCalculators.put("bus", FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/busFareGTFS.json"));
		fareCalculators.put("minibus", busFareGetter.get());
		fareCalculators.put("LR", new LRFareCalculator("fare/light_rail_fares.csv"));
		fareCalculators.put("ferry",FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/ferryFareGTFS.json"));
		fareCalculators.put("tram", new UniformFareCalculator(2.6));
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	double startTime = config.qsim().getStartTime().seconds();
	double endTime = config.qsim().getEndTime().seconds();
	Map<String,Tuple<Double,Double>> timeBeans = new HashMap<>();
	int hour = ((int)startTime/3600)+1;
	for(double ii = 0; ii < endTime; ii = ii+3600) {
		timeBeans.put(Integer.toString(hour), new Tuple<>(ii,ii+3600));
		hour = hour + 1;
	}
	operatorVariables.entrySet().forEach(op->{
		operatorOptimizers.put(op.getKey(), new ScaledAdam(op.getKey(),op.getValue()));
	});
	
	String type =  MaaSDataLoaderV2.typeGovt;
	String fileLoc = "test/"+operatorID+type+"_WithCostOptimIterBreakEven.csv";
	String usageFileLoc = "test/"+operatorID+type+"_WithCostUsageDetailsBreakEven.csv";
	
	FileWriter fw = new FileWriter(new File(fileLoc));
	fw.append("optimIter");
	List<String> vName = new ArrayList<>();
	for(String sss:Param.keySet()) {
		fw.append(","+MaaSUtil.retrieveName(sss)+"_gradient");
		vName.add(sss);
	}
	for(String sss:vName) {
		fw.append(","+MaaSUtil.retrieveName(sss));
	}
	
	FileWriter fwU = new FileWriter(new File(usageFileLoc));
	fw.append("optimIter");
	
	
	
	List<String> operators = new ArrayList<>();
	operatorOptimizers.entrySet().forEach(o->{
		try {
			fw.append(","+o.getKey()+"_Objective");
			operators.add(o.getKey());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	});
	fw.append("\n");
	IntelligentOperatorDecisionEngineV2 modelHandler = new IntelligentOperatorDecisionEngineV2(scenario, pacAll, new timeBeansWrapper(timeBeans), fareCalculators, null,type);
	for(Entry<String, Map<String, VariableDetails>> d:operatorVariables.entrySet()) {
		for(Entry<String, VariableDetails> v:d.getValue().entrySet())
		modelHandler.addNewVariable(d.getKey(),v.getKey(), v.getValue());
	}
	
	if(optimizeForBreakEven == true && initialTotalSystemTravelTimeInMoney == null) {
		PersonPlanSueModel model = new PersonPlanSueModel(timeBeans, config);
		Population withOutMaaSPop = PopulationUtils.readPopulation("toyScenarioLarge/output_withoutMaaS/output_plans.xml.gz");
		model.populateModel(scenario, fareCalculators, pacAll);
		SUEModelOutput flow = model.performAssignment(withOutMaaSPop, new LinkedHashMap<>());
		double vot_car = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithCar_NAME).getOrCreateModeParams("car").getMarginalUtilityOfTraveling()/3600;
		double vot_transit = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getOrCreateModeParams("pt").getMarginalUtilityOfTraveling()/3600;
		double vot_wait = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtlOfWaitingPt_utils_hr()/3600;
		double vom = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtilityOfMoney();
		initialTotalSystemTravelTimeInMoney = ObjectiveAndGradientCalculator.calcTotalSystemTravelTime(model, flow,vot_car,vot_transit, vot_wait, vom);
		FileWriter fwTTBase = new FileWriter(new File("toyScenarioLarge/output_withoutMaaS/TTBase.txt"));
		fwTTBase.append("totalSystemTTinMoney = "+initialTotalSystemTravelTimeInMoney);
		fwTTBase.flush();
		fwTTBase.close();
	}
	
	
	for(int counter = 0;counter<100;counter++) {
		//System.out.println("GB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024*1024));
	
		fw.append(counter+"");
		long t = System.currentTimeMillis();
		Map<String,Map<String,Double>>grad =  modelHandler.calcApproximateObjectiveGradient();
		if(optimizeForBreakEven) {
			
		}
		
//		if(counter == 1) {
//		Map<String,Map<String,Double>>fdgrad =  this.decisionEngine.calcFDGradient();//This line is for testing only. 
//		
//		logger.info("grad = "+ grad);
//		logger.info("FD Grad = "+ fdgrad);
//	}
		Map<String,Double> obj = modelHandler.calcApproximateObjective();
		
		if(optimizeForBreakEven) {
			final double tt = initialTotalSystemTravelTimeInMoney;
			obj.entrySet().forEach(e->e.setValue(e.getValue()-tt));
			grad.entrySet().forEach(e->{
				e.getValue().entrySet().forEach(ee->ee.setValue(ee.getValue()*obj.get(e.getKey())));
			});
		}
		//double ttObj = this.decisionEngine.calcApproximateGovtTTObjective(this.decisionEngine.getVariables)
		if(counter%10==0||counter==99) {
		packUsageStat stat = modelHandler.getPackageUsageStat();
		List<String>ops = new ArrayList<>(stat.packagesSold.keySet());
		
		fwU.append("optimIter = "+counter);
		for(String op:ops) {
			fwU.append(","+op);
		}
		fwU.append("\n");
		fwU.append("PacakgeSold");
		for(String op:ops) {
			fwU.append(","+stat.packagesSold.get(op));
		}
		fwU.append("\n");
		fwU.append("selfPackageTrip");
		for(String op:ops) {
			fwU.append(","+stat.selfPackageTrip.get(op));
		}
		fwU.append("\n");
		fwU.append("PacakgeTrip");
		for(String op:ops) {
			fwU.append(","+stat.packageTrip.get(op));
		}
		fwU.append("\n");
		
		fwU.append("Revenue");
		for(String op:ops) {
			fwU.append(","+stat.revenue.get(op));
		}
		fwU.append("\n");
		
		fwU.append("totalTrip");
		for(String op:ops) {
			fwU.append(","+stat.totalTrip.get(op));
		}
		fwU.append("\n");
		fwU.flush();
		}
		if(grad==null)
			logger.debug("Gradient is null. Debug!!!");
	
	
	
		operatorOptimizers.entrySet().forEach(o->{
			o.getValue().takeStep(grad.get(o.getKey()));//This step 
			//already decides the new variable values and replace the old one with the new values. As, the same variable details instances
			//are used in decision engine and also here, the change should be broadcasted automatically. (Make a check if possible)Ashraf July 11, 2020
			//o.getValue().takeStep(null);
			for(String sss:vName) {
				try {
					if(grad.get(o.getKey()).get(sss)==null) {
						String ssss = MaaSUtil.retrieveName(sss);
						logger.debug("gradient is null here. Debug!!!!");
					}
					fw.append(","+grad.get(o.getKey()).get(sss));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
			Map<String,Double> variableValues = new HashMap<>();
			for(Entry<String, VariableDetails> vd:Param.entrySet()) {
				variableValues.put(vd.getKey(), vd.getValue().getCurrentValue());
			}
			
			MaaSUtil.updateMaaSVariables(pacAll, variableValues, scenario.getTransitSchedule(),modelHandler.getSimpleVariableKey());
			for(String sss:vName) {
				fw.append(","+variableValues.get(sss));
			}
			for(String sss:operators) {
				fw.append(","+obj.get(sss));
			}
			fw.append("\n");
			fw.flush();
			
		System.out.println("Used Memory in GB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024*1024));
		System.out.println("Time Required for iteratio "+counter+" = "+(System.currentTimeMillis()-t)/1000+" seconds.");
		//if(gradNorm<10)break;
		
	}
	
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
