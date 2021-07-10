package elasticDemand;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
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
import maasPackagesV2.MaaSPackage;
import maasPackagesV2.MaaSPackages;
import maasPackagesV2.MaaSPackagesReader;
import maasPackagesV2.MaaSPackagesWriter;
import optimizer.Adam;
import optimizer.Optimizer;
import optimizer.ScaledAdam;
import optimizerAgent.IntelligentOperatorDecisionEngineV2;
import optimizerAgent.MaaSOperator;
import optimizerAgent.MaaSUtil;
import optimizerAgent.ObjectiveAndGradientCalculator;
import optimizerAgent.PersonPlanSueModel;
import optimizerAgent.SelectedPlanPopulationCompressor;
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
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SignalFlowReductionGenerator;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class MetaModelRunWithElasticDemandV2 {
	public final static String PriceVarType = "priceVar";
	public final static String DiscountVarType = "discountVar";
	public static final Logger logger = Logger.getLogger(MetaModelRunWithElasticDemand.class);
	public static void main(String[] args) throws IOException {
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
		
		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";

		
		
		//parameters

		String maasOwner = "Govt";//"Govt";// MTR, bus, separate
		boolean optimizeForBreakEven = false;
		boolean optimizeForCombinedBreakEven = false;
		boolean optimizeForCombinedRevenue = true;
		boolean logAllGradient = true;

		
		Double initialTotalSystemTravelTimeInMoney = 10.; 		

		Map<String,Double> breakEvenTarget = new HashMap<>();
		breakEvenTarget.put("bus", 1036815.461485263);//Previously taken 687123.679044381
		breakEvenTarget.put("MTR", 1565757.8901090259);//Previously taken 1678198.3128105325
		breakEvenTarget.put("ferry", 6433.161727042853);//previously taken 11015.027733073915
		breakEvenTarget.put("Govt", 5.284980419851977E8);//tstt = -8256001.499058168
		

		
		String refinedPopLoc = "test\\populations\\withMaaSPopulationMay27Allpac.xml";// "test\\populations\\extPopulation_allPackGovt.xml"
		String MaaSPacakgeFileLoc = "test/packages_July2020_20.xml";
		String newMaaSWriteLoc = "test/packages_"+maasOwner+".xml";
		String averageDurationMapFileLoc = "test/actAverageDurations.csv";
		//use null in the limits to avoid any variable
		Tuple<Double,Double> discountVarLimits = null;//new Tuple<Double,Double>(0.5,1.0);
		Tuple<Double,Double> priceVarLimits = new Tuple<Double,Double>(5.0,50.0);
		Tuple<Double,Double> reimbursementVarLimits = new Tuple<Double,Double>(0.5,1.0); // new Tuple<Double,Double>(0.5,1.0);
		double initDiscount = 0.8;
		double initReimbursement = 0.9;
		Double packagePrice = 10.0;//Make it null to initiate from the original package price 
		

		String type =  MaaSDataLoaderV2.typeGovtTU;
		String fileLoc = "test/GovtBreakEven2/"+maasOwner+type+"_CPMaxTUOptimIterJul4_negMoney.csv";
		String usageFileLoc = "test/GovtBreakEven2/"+maasOwner+type+"_CPUsageDetailsMaxTUJul4_negMoney.csv";
		String varLocation = "test/GovtBreakEven2/variables"+maasOwner+type+"_CPMaxTUVarsJul4_negMoney.csv";
		String metaPopsub = "test/GovtBreakEven2/"+maasOwner+type+"Jul4";

		double reimbursementRatio = 1.0;
		String baseCaseLoc = "test/populations/extPopulation_NoMaaSMay17.xml";
		String baseTTWriteLoc = "test/GovtBreakEven2/output_plans_WithoutMaaS_115_Calibrated_noMaasAndMaasAllPackJul4.csv";
		String combinedPopWriteLoc = "test/GovtBreakEven2/CombinedPopGovtJun24.xml";
		List<String> operatorsObjectiveToWrite = new ArrayList<>();
		
		Map<String,Double> anaParams = new HashMap<>();
		//LinkMiu	ModeMiu	BPRalpha	BPRbeta	Transferalpha	Transferbeta	pathSizeConst

		//0.417365406	3.824547207	6.829215846	2.265271803	2.042034559	43.13903973
		//1.818738825	0.5	1.675718742	4.303474896	2.028420358	0.944423396	44.00672215


		anaParams.put("LinkMiu", 1.818738825);
		anaParams.put("ModeMiu", 0.5);
		anaParams.put("BPRalpha", 1.675718742);
		anaParams.put("BPRbeta", 4.303474896);
		anaParams.put("Transferalpha", 2.028420358);
		anaParams.put("Transferbeta", 0.944423396);
		anaParams.put("pathSizeConst", 44.00672215);
		
		ParamReader pReader = new ParamReader("test\\GovtBreakEven2\\Calibration\\subPopParamAndLimit_Calibrated.csv");
		
		
		//Load the MaaS Packages
		
		MaaSPackages pac = new MaaSPackagesReader().readPackagesFile(MaaSPacakgeFileLoc);//Read the original
		MaaSPackages pacAll = null;
		//converting to operator unified
		if(maasOwner!=null) {
			pacAll = MaaSUtil.createUnifiedMaaSPackages(pac, maasOwner, "allPack");
		
		
			pac = null;
			pacAll.setAllOPeratorReimbursementRatio(reimbursementRatio);//set reimbursement ratio
			pacAll.getMassPackagesPerOperator().get(maasOwner).forEach(m->m.getOperatorReimburesementRatio().put(maasOwner, 1.0));
			new MaaSPackagesWriter(pacAll).write(newMaaSWriteLoc);//write down the modified packages
		}else {
			pacAll = pac;
			new MaaSPackagesWriter(pacAll).write(newMaaSWriteLoc);
		}
		
		
		Config config = singlePlanAlgo.RunUtils.provideConfig();
		config.plans().setInputFile(refinedPopLoc);//Set the refined pop location 
		
		
		config.addModule(new MaaSConfigGroup());
		config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,newMaaSWriteLoc);
		
		pReader.SetParamToConfig(config, pReader.getInitialParam());
		
		//config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packages_"+operatorID+".xml");
		
		config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		config.plans().setInputPersonAttributeFile("new Data/core/personAttributesHKI.xml");
		//config.plans().setInputFile("toyScenarioLarge/output_optim_operatorPlatformGovt/output_plans.xml.gz");
		
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
		Set<Set<Id<TransitLine>>> tlSets = new RandomCluster<Id<TransitLine>>().createRandomSplit(scenario.getTransitSchedule().getTransitLines().keySet(), 20);
		//	Set<Set<Id<TransitLine>>> tlSets = new UsageBasedCluster<TransitLine>().createUsageBasedSplit(scenario.getTransitSchedule().getTransitLines(), 
		Map<String,Double>avgDur = ObjectiveAndGradientCalculator.readSimpleMap(averageDurationMapFileLoc,true);
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
						if(avgDur.get(act.getType())!=null||avgDur.get(act.getType())!=0) {
							params.setTypicalDuration(avgDur.get(act.getType()));
						}
						else {
							params.setTypicalDuration(3600);
						}
						if(act.getType().equalsIgnoreCase("Place nearby to home / downstairs (please specify)")) {
							System.out.println();
						}
						config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).addActivityParams(params);
						config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).addActivityParams(params);
						config.planCalcScore().getScoringParameters(PersonFixed_NAME).addActivityParams(params);
						config.planCalcScore().getScoringParameters(GVChange_NAME).addActivityParams(params);
						config.planCalcScore().getScoringParameters(GVFixed_NAME).addActivityParams(params);
					}else {
						if(act.getType().equals("Place nearby to home / downstairs")) {
							System.out.println();
						}
					}
				}
			}
			
			
		}
		Map<String,Map<String,VariableDetails>> variables  = createVariables(pacAll,tlSets,priceVarLimits,discountVarLimits,reimbursementVarLimits,initDiscount,initReimbursement,packagePrice);
		Map<String,VariableDetails> Param = new HashMap<>();
		variables.entrySet().forEach(e->e.getValue().entrySet().stream().forEach(ee->Param.put(ee.getKey(), ee.getValue())));
		
		Map<String,Optimizer> operatorOptimizers = new HashMap<>();
	
	
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
		variables.entrySet().forEach(op->{
			operatorOptimizers.put(op.getKey(), new ScaledAdam(op.getKey(),op.getValue(),.005,.9,.999,1e-5,100,1000));
		});
		
		List<String>allVars = new ArrayList<>();
		variables.entrySet().forEach(e->allVars.addAll(e.getValue().keySet()));
	
	
		FileWriter fw = new FileWriter(new File(fileLoc),true);
		fw.append("optimIter");
		Map<String,List<String>> vName = new HashMap<>();
		for(String oo:operatorOptimizers.keySet()) {
			vName.put(oo, new ArrayList<>());
			if(!logAllGradient) {
				for(String sss:variables.get(oo).keySet()) {
					fw.append(","+oo+"_"+MaaSUtil.retrieveName(sss)+"_gradient");
					vName.get(oo).add(sss);
				}
			}else {
				for(String sss:allVars) {
					fw.append(","+oo+"_"+MaaSUtil.retrieveName(sss)+"_gradient");
					vName.get(oo).add(sss);
				}
			}
		}
		for(String sss:allVars) {
			
			fw.append(","+MaaSUtil.retrieveName(sss));
			
		}
	
		FileWriter fwU = new FileWriter(new File(usageFileLoc),true);
		fw.append("optimIter");
	
		
	
		
		if(optimizeForCombinedBreakEven||optimizeForCombinedRevenue) {
			
			operatorsObjectiveToWrite.addAll(pacAll.getOPeratorList());
		
		}else {
			operatorsObjectiveToWrite.addAll(operatorOptimizers.keySet());
		}
		
		operatorsObjectiveToWrite.forEach(o->{
		try {
			fw.append(","+o+"_Objective");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		});
		fw.append("\n");
		fw.flush();
		Map<String,Double> maasCount = new HashMap<>();
		for(Entry<Id<Person>, ? extends Person> p:scenario.getPopulation().getPersons().entrySet()){
			for(Plan pl:p.getValue().getPlans()) {
				String maas = (String) pl.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
				if(maas!=null && maas.equals("ferry")) {
					pl.getAttributes().removeAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
					maas=null;
				}
				maasCount.compute(maas, (k,v)->v==null?1:v+1);
			}
		}
		System.out.println(maasCount);
		IntelligentOperatorDecisionEngineV2 modelHandler = new IntelligentOperatorDecisionEngineV2(scenario, pacAll, new timeBeansWrapper(timeBeans), fareCalculators, null,type);
		
		
		for(Entry<String, Map<String, VariableDetails>> d:variables.entrySet()) {
			for(Entry<String, VariableDetails> v:d.getValue().entrySet()) {
				modelHandler.addNewVariable(d.getKey(),v.getKey(), v.getValue());
			}
			
		}
		
		if(optimizeForCombinedBreakEven || optimizeForCombinedRevenue) {
			modelHandler.setIfCalculateFull(true);
		}
		
	
		Map<String,VariableDetails> vars = new HashMap<>();
		variables.entrySet().forEach(vv->vars.putAll(vv.getValue()));
		writeVars(varLocation,vars);
		double initialTotalSystemUtilityInMoney = 0;
		if(optimizeForBreakEven == true && initialTotalSystemTravelTimeInMoney == null) {
			PersonPlanSueModel model = new PersonPlanSueModel(timeBeans, config);
			Population withOutMaaSPop = PopulationUtils.readPopulation(baseCaseLoc);
			
			Population maasPopulation = scenario.getPopulation();
			
			MaaSUtil.mergePopulations( maasPopulation, withOutMaaSPop, combinedPopWriteLoc,3);
			Map<String,Double> maasCount1 = new HashMap<>();
			maasPopulation.getPersons().values().forEach(p->{
				if(!PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
					p.getPlans().forEach(pl->{
						String m = (String) pl.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
						maasCount1.compute(m, (k,v)->v==null?1:v+1);	
					});
					
				}
				
			});
			System.out.println(maasCount1);
			new PopulationWriter(maasPopulation).write("test/GovtBreakEven/withMaaSPopulationMay27Allpac.xml");
			
			maasPopulation.getPersons().values().forEach(p->{
				p.getPlans().forEach(pl->pl.getAttributes().removeAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName));
				
			});
			new PopulationWriter(maasPopulation).write("test/GovtBreakEven/withoutMaaSPopulationMay27Allpac.xml");
			
			
			//model.setPopulationCompressor(new SelectedPlanPopulationCompressor());
			model.populateModel(scenario, fareCalculators, pacAll);
			model.getInternalParamters().putAll(anaParams);
//			modelHandler.getModel().getInternalParamters().putAll(anaParams);
			SUEModelOutput flow = model.performAssignment(maasPopulation, new LinkedHashMap<>());
			double vot_car = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithCar_NAME).getOrCreateModeParams("car").getMarginalUtilityOfTraveling()/3600;
			double vot_transit = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getOrCreateModeParams("pt").getMarginalUtilityOfTraveling()/3600;
			double vot_wait = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtlOfWaitingPt_utils_hr()/3600;
			double vom = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtilityOfMoney();
			initialTotalSystemTravelTimeInMoney = ObjectiveAndGradientCalculator.calcTotalSystemTravelTime(model, flow,vot_car,vot_transit, vot_wait, vom);
			initialTotalSystemUtilityInMoney = ObjectiveAndGradientCalculator.calcTotalSystemUtilityGradientAndObjective(model).getSecond();
			FileWriter fwTTBase = new FileWriter(new File(baseTTWriteLoc));
			fwTTBase.append("totalSystemTTinMoney = ,"+initialTotalSystemTravelTimeInMoney+"\n");
			fwTTBase.append("totalSystemUTinMoney = ,"+initialTotalSystemUtilityInMoney+"\n");
			fwTTBase.flush();
			
			packUsageStat stat = ObjectiveAndGradientCalculator.calcPackUsageStat(flow, pacAll, fareCalculators);
			List<String>ops = new ArrayList<>(stat.totalTrip.keySet());
			
			fwTTBase.append("optimIter = "+"baseCase");
			for(String op:ops) {
				fwTTBase.append(","+op);
			}
			fwTTBase.append("\n");
			fwTTBase.append("PacakgeSold");
			for(String op:ops) {
				fwTTBase.append(","+stat.packagesSold.get(op));
			}
			fwTTBase.append("\n");
			fwTTBase.append("selfPackageTrip");
			for(String op:ops) {
				fwTTBase.append(","+stat.selfPackageTrip.get(op));
			}
			fwTTBase.append("\n");
			fwTTBase.append("PacakgeTrip");
			for(String op:ops) {
				fwTTBase.append(","+stat.packageTrip.get(op));
			}
			fwTTBase.append("\n");
			
			fwTTBase.append("Revenue");
			for(String op:ops) {
				fwTTBase.append(","+stat.revenue.get(op));
			}
			fwTTBase.append("\n");
			
			fwTTBase.append("totalTrip");
			for(String op:ops) {
				fwTTBase.append(","+stat.totalTrip.get(op));
			}
			fwTTBase.append("\n");
			fwTTBase.append("autoFlow = "+flow.getAutoFlow()+"\n");
			fwTTBase.flush();
			fwTTBase.close();
			throw new IllegalArgumentException("Finished generating base case and population!!!Stopping");
		}
			
	int maasPlan = 0;	
	for(Person p:scenario.getPopulation().getPersons().values()) {
		for(Plan pl:p.getPlans()) {
			if(pl.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName)!=null)maasPlan++;
		}
	}
	modelHandler.setAnaParams(anaParams);
	for(int counter = 0;counter<100;counter++) {
		//System.out.println("GB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024*1024));
	
		fw.append(counter+"");
		long t = System.currentTimeMillis();
		Map<String,Map<String,Double>>grad =  modelHandler.calcApproximateObjectiveGradient();
	
		
//		if(counter == 1) {
//		Map<String,Map<String,Double>>fdgrad =  this.decisionEngine.calcFDGradient();//This line is for testing only. 
//		
//		logger.info("grad = "+ grad);
//		logger.info("FD Grad = "+ fdgrad);
//	}
		Map<String,Double> obj = modelHandler.calcApproximateObjective();
		Map<String,Double> objNew = null;
		if(optimizeForBreakEven) {
//			final double tt = initialTotalSystemTravelTimeInMoney;
//			obj.entrySet().forEach(e->e.setValue(e.getValue()-tt));
			Tuple<Map<String, Double>, Map<String, Double>> be =  ObjectiveAndGradientCalculator.calcBreakEvenGradient(grad, obj, breakEvenTarget);
			objNew = be.getSecond();
			grad.put(maasOwner, be.getFirst());
//			grad.entrySet().forEach(e->{
//				e.getValue().entrySet().forEach(ee->ee.setValue(-1*ee.getValue()*obj.get(e.getKey())));
//			});
		}else if(optimizeForCombinedRevenue) {
			Tuple<Map<String, Double>, Map<String, Double>> be =  ObjectiveAndGradientCalculator.calcOptimizedBreakEvenGradient(grad, obj, breakEvenTarget,maasOwner);
			objNew = be.getSecond();
			grad.put(maasOwner, be.getFirst());
		}
		if(modelHandler.getPackageUsageStat().additionalGovtGrad!=null) {
			grad.get("Govt").entrySet().forEach(g->{
				g.setValue(g.getValue()+modelHandler.getPackageUsageStat().additionalGovtGrad.get(g.getKey()));
			});
		}
		//obj = objNew;
		//double ttObj = this.decisionEngine.calcApproximateGovtTTObjective(this.decisionEngine.getVariables)
		if(counter%5==0||counter==99) {
		packUsageStat stat = modelHandler.getPackageUsageStat();
		List<String>ops = new ArrayList<>(stat.packagesSold.keySet());
		
		double vot_car = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithCar_NAME).getOrCreateModeParams("car").getMarginalUtilityOfTraveling()/3600;
		double vot_transit = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getOrCreateModeParams("pt").getMarginalUtilityOfTraveling()/3600;
		double vot_wait = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtlOfWaitingPt_utils_hr()/3600;
		double vom = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtilityOfMoney();
		initialTotalSystemTravelTimeInMoney = ObjectiveAndGradientCalculator.calcTotalSystemTravelTime(modelHandler.getModel(), modelHandler.getFlow(),vot_car,vot_transit, vot_wait, vom);
		initialTotalSystemUtilityInMoney = ObjectiveAndGradientCalculator.calcTotalSystemUtilityGradientAndObjective(modelHandler.getModel()).getSecond();
	
		fwU.append("totalSystemTTinMoney = ,"+initialTotalSystemTravelTimeInMoney+"\n");
		fwU.append("totalSystemUTinMoney = ,"+initialTotalSystemUtilityInMoney+"\n");
		
		fwU.append("Govt money revenue = ,"+ stat.GovtSepRevenue+"\n");
		
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
		fwU.append("autoFlow = "+modelHandler.getFlow().getAutoFlow()+"\n");
		fwU.flush();
		}
		if(grad==null)
			logger.debug("Gradient is null. Debug!!!");
	
	
	
		operatorOptimizers.entrySet().forEach(o->{
			o.getValue().takeStep(grad.get(o.getKey()));//This step 
			//already decides the new variable values and replace the old one with the new values. As, the same variable details instances
			//are used in decision engine and also here, the change should be broadcasted automatically. (Make a check if possible)Ashraf July 11, 2020
			//o.getValue().takeStep(null);
			for(String sss:vName.get(o.getKey())) {
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
			for(String sss:allVars) {
				fw.append(","+variableValues.get(sss));
			}
			for(String sss:operatorsObjectiveToWrite) {
				fw.append(","+obj.get(sss));
			}
			fw.append("\n");
			fw.flush();
			
		System.out.println("Used Memory in GB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024*1024));
		System.out.println("Time Required for iteratio "+counter+" = "+(System.currentTimeMillis()-t)/1000+" seconds.");
		//if(gradNorm<10)break;
		if(counter%25==0 ||counter == 99)new PopulationWriter(modelHandler.getModel().getPopulation()).write(metaPopsub+"metaModelPop"+counter+".xml");
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

/**
 * 
 * @param pacs
 * @param tlSets
 * @param priceVarsLimit if null then price variables are ignored
 * @param discountVaraibleLimits if null then discount variables are ignored
 * @return
 */
public static Map<String,Map<String,VariableDetails>> createVariables(MaaSPackages pacs, Set<Set<Id<TransitLine>>>tlSets, Tuple<Double,Double>priceVarsLimit,Tuple<Double,Double>discountVaraibleLimits,Tuple<Double,Double>reimbursementVarsLimits,double initDiscount,double initReimbursement,Double pacPrice){
	Map<String,Map<String,VariableDetails>> variables = new HashMap<>();
	pacs.getMassPackagesPerOperator().entrySet().forEach(o->{
		variables.put(o.getKey(),new HashMap<>());
		for(MaaSPackage pac:o.getValue()) {
			if(priceVarsLimit!=null) {
				Double p = pacPrice;
				if(pacPrice==null) {
					p = pac.getPackageCost();
				}
				VariableDetails price = new VariableDetails(MaaSUtil.generateMaaSPackageCostKey(pac.getId()),priceVarsLimit,p);
				variables.get(o.getKey()).put(price.getVariableName(), price);
			}
			if(discountVaraibleLimits!=null) {
				int i = 0;
				for(Set<Id<TransitLine>>tlSet:tlSets) {
					VariableDetails tl = new VariableDetails(MaaSUtil.generateMaaSTransitLinesDiscountKey(pac.getId(), tlSet, "tl"+i),discountVaraibleLimits,initDiscount);
					variables.get(o.getKey()).put(tl.getVariableName(),tl);
					i++;
				}
			}
			
			if(reimbursementVarsLimits!=null) {
				for(String flOp:pac.getOperatorSpecificFareLinks().keySet()) {
					double rr = initReimbursement;
					if(flOp.equals(pac.getOperatorId()))rr = 1.0;
					VariableDetails tl = new VariableDetails(MaaSUtil.generateMaaSPackageFareOperatorReimbursementRatioKey(pac.getId(), flOp),reimbursementVarsLimits,rr);
					variables.get(o.getKey()).put(tl.getVariableName(),tl);
				}
			}
		}
		
		});
	
	return variables;
}


public static void writeVars(String fileLoc, Map<String,VariableDetails> vars) {
	try {
		FileWriter fw = new FileWriter(new File(fileLoc),true);
		fw.append("varKey,varDetails\n");
		vars.entrySet().forEach(v->{
			try {
				fw.append(v.getKey()+","+v.getValue().toString()+"\n");
				fw.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		fw.close();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
}

}

