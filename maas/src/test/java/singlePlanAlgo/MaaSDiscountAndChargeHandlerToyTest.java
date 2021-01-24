package singlePlanAlgo;



import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
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
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.xml.sax.SAXException;

import com.google.common.collect.Maps;


import MaaSPackagesV2.ConfigGenerator;
import MaaSPackagesV2.FareCalculatorCreator;
import maasPackagesV2.MaaSPackage;
import maasPackagesV2.MaaSPackages;
import maasPackagesV2.MaaSPackagesReader;
import maasPackagesV2.MaaSPackagesWriter;
import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import matsimIntegrate.DynamicRoutingModuleWithMaas;
import optimizerAgent.MaaSOperator;
import optimizerAgent.MaaSOperatorOptimizationModule;
import optimizerAgent.MaaSOperatorStrategy;
import optimizerAgent.MaaSUtil;
import optimizerAgent.ObjectiveAndGradientCalculator;
import running.RunUtils;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import clustering.RandomCluster;
import clustering.UsageBasedCluster;
/**
 * This class will basically test the connection between different components for MaaS implementation in MATSim.
 * This however, will not test the optimization part of MaaS
 * @author ashraf
 *
 */
class MaaSDiscountAndChargeHandlerToyTest {
public static void main(String[] args) {
	
//}
//	@Test
//	void test() {
		
	
		
		// This will test the optimization agent insertion
				Config config = ConfigGenerator.generateToyConfig();
				new ConfigWriter(config).write("RunUtilsToyConfig.xml");
				//OutputDirectoryLogging.catchLogEntries();
				config.addModule(new MaaSConfigGroup());
				config.controler().setLastIteration(150);
//				MaaSPackages pac = new MaaSPackagesReader().readPackagesFile("test/packages_all.xml");
//				pac.getMassPackages().values().forEach(p->p.setReimbursementRatio(0.9));
//				new MaaSPackagesWriter(pac).write("test/packages_all.xml");
				MaaSPackages pac = new MaaSPackagesReader().readPackagesFile("test/packagesToy_July2020_20.xml");
				MaaSPackages pacAll = MaaSUtil.createUnifiedMaaSPackages(pac, "Govt", "allPack");
				//pacAll.getMassPackages().get("platform").setSelfFareLinks(new HashSet<>());
//				Set<MaaSPackage> pacIds = pac.getMassPackagesPerOperator().get("train");
				
				//config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packages_all.xml");
				//config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"packages_July2020_400.xml");
				
				String operatorID = "Govt";
				
//				new HashMap<>(pac.getMassPackagesPerOperator()).entrySet().forEach(oPacs->{
//					if(!oPacs.getKey().equals(operatorID)) {
//						oPacs.getValue().forEach(p->{
//							pac.removeMaaSPackage(p);
//						});
//					}else {
//						oPacs.getValue().forEach(p->{
//							p.setPackageCost(30);
//						});
//					}
//				});
				
//				new HashMap<>(pac.getMassPackagesPerOperator()).entrySet().forEach(oPacs->{
//					if(oPacs.getKey().equals(operatorID)) {
//						oPacs.getValue().forEach(p->{
//							
//							pacAll.getMassPackages().get("platform").getSelfFareLinks().addAll(p.getFareLinks().keySet());
//							
//						});
//					}
//				});
				pacAll.setAllOPeratorReimbursementRatio(0.9);
				new MaaSPackagesWriter(pacAll).write("test/packagesToy_"+operatorID+".xml");
				config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packagesToy_"+operatorID+".xml");
				//new MaaSPackagesWriter(pac).write("test/packages_"+operatorID+".xml");
				
				//config.getModules().get(MaaSConfigGroup.GROUP_NAME).addParam(MaaSConfigGroup.INPUT_FILE,"test/packages_"+operatorID+".xml");
				
				config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
				//config.plans().setInputPersonAttributeFile("new Data/core/personAttributesHKI.xml");
//				config.plans().setInputFile("new Data/core/20.plans.xml.gz");
				config.controler().setOutputDirectory("toyScenario/output_optim_operatorPlatform"+operatorID);
				config.controler().setWritePlansInterval(50);
				
//				
				RunUtils.createStrategies(config, "normalAgent", 0.015, 0.015, 0.01, 0);
			
				RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), "normalAgent", 
						0.05, 100);
				RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), "normalAgent", 
						0.01, 100);
				
		

				
				//Add the MaaS package choice strategy
				config.strategy().addStrategySettings(createStrategySettings(MaaSPlanStrategy.class.getName(),.05,225,"normalAgent"));
	
				config.strategy().addStrategySettings(createStrategySettings(MaaSOperatorStrategy.class.getName(),1,200,MaaSUtil.MaaSOperatorAgentSubPopulationName));
				
				config.strategy().addStrategySettings(createStrategySettings(UnnecessaryMaaSPlanRemovalStrategy.class.getName(),.05,225,"normalAgent"));
				
				
				
				//___________________
				
//				RunUtils.addStrategy(config, "KeepLastSelected", MaaSUtil.MaaSOperatorAgentSubPopulationName, 
//						1, 400);
				
				
				config.global().setNumberOfThreads(3);
				config.qsim().setNumberOfThreads(3);				
				ParamReader pReader=new ParamReader("src/main/resources/toyScenarioData/paramReaderToy.csv");
				pReader.setDefaultParams(config, "normalAgent");
				pReader.setDefaultParams(config, MaaSOperator.type);
				pReader.setDefaultParams(config, PlanCalcScoreConfigGroup.DEFAULT_SUBPOPULATION);
				
				
				new ConfigWriter(config).write("test/config.xml");
				
				Scenario scenario = ScenarioUtils.loadScenario(config);
				
				Set<Set<Id<TransitLine>>> tlSets = new RandomCluster<Id<TransitLine>>().createRandomSplit(scenario.getTransitSchedule().getTransitLines().keySet(), 4);
//				Set<Set<Id<TransitLine>>> tlSets = new UsageBasedCluster<TransitLine>().createUsageBasedSplit(scenario.getTransitSchedule().getTransitLines(), 
//						ObjectiveAndGradientCalculator.readSimpleMap("test/ averageWaitTime.csv"), 20);
				writeTLsets(tlSets,"toyScenario/output_optim_operatorPlatform"+operatorID+"/tlSets.csv");
				ObjectAttributes obj = new ObjectAttributes();
				//new ObjectAttributesXmlReader(obj).readFile("new Data/core/personAttributesHKI.xml");
				
				for(Person person: scenario.getPopulation().getPersons().values()) {
//					String subPop = (String) obj.getAttribute(person.getId().toString(), "SUBPOP_ATTRIB_NAME");
					PopulationUtils.putSubpopulation(person, "normalAgent");
					Id<Vehicle> vehId = Id.create(person.getId().toString(), Vehicle.class);
					Map<String, Id<Vehicle>> modeToVehicle = Maps.newHashMap();
					modeToVehicle.put("taxi", vehId);
					modeToVehicle.put("car", vehId);
					VehicleUtils.insertVehicleIdsIntoAttributes(person, modeToVehicle);
					for(PlanElement pe :person.getSelectedPlan().getPlanElements()) {
						if (pe instanceof Activity) {
							Activity act = (Activity)pe;
							
							if(scenario.getConfig().planCalcScore().getScoringParameters("normalAgent").getActivityParams(act.getType()) == null) {
								ActivityParams params = new ActivityParams(act.getType());
								params.setTypicalDurationScoreComputation(TypicalDurationScoreComputation.uniform);
								params.setMinimalDuration(3600);
								params.setTypicalDuration(8*3600);
								config.planCalcScore().getScoringParameters("normalAgent").addActivityParams(params);
						
							}
						}
					}
				}
				
//				for(Link link:scenario.getNetwork().getLinks().values()) {
//					link.setCapacity(link.getCapacity()*.14);
//				}
				
				MaaSPackages packages = new MaaSPackagesReader().readPackagesFile(scenario.getConfig().getModules().get(MaaSConfigGroup.GROUP_NAME).getParams().get(MaaSConfigGroup.INPUT_FILE)); //It has to be consistent with the config.
				//RunUtils.scaleDownPopulation(scenario.getPopulation(), 0.1);
				Map<String,Map<String,Double>> variables  = new HashMap<>();
				Map<String,Map<String,Tuple<Double,Double>>> variableLimits  = new HashMap<>();
				variables.put("Govt", new HashMap<>());
				variableLimits.put("Govt", new HashMap<>());
				int i=0;
				for(Set<Id<TransitLine>>sss:tlSets){
					variables.get("Govt").put(MaaSUtil.generateMaaSTransitLinesDiscountKey("allPack", sss,"tl"+i),0.8);
					variableLimits.get("Govt").put(MaaSUtil.generateMaaSTransitLinesDiscountKey("allPack", sss, "tl"+i),new Tuple<Double,Double>(0.5,1.));
					i++;
				};
//				variables.get("Govt").keySet().forEach(v->{
//					for(Id<TransitLine>lineId:MaaSUtil.retrieveTransitLineId(v)) {
//						MaaSUtil.getTransitLineToFareLinkIncidence(lineId, scenario.getTransitSchedule(), MaaSUtil.retrievePackageId(v), packages);	
//					}
//				});
				
				Activity act = MaaSUtil.createMaaSOperator(packages, scenario.getPopulation(), "test/agentPopToy.xml",new Tuple<>(.5,4.5),variables,variableLimits,true);
				
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
				controler.addOverridingModule(new MaaSDataLoaderV2(MaaSDataLoaderV2.typeGovt));
				//controler.addOverridingModule(new MaaSOperatorOptimizationModule("new Data/data/odNetwork.xml",5));
				controler.addOverridingModule(new MaaSOperatorOptimizationModule());
				//ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
				SAXParser saxParser;
				
				try {
					//saxParser = SAXParserFactory.newInstance().newSAXParser();
					//saxParser.parse("new Data/data/busFare.xml", busFareGetter);
					controler.addOverridingModule(new DynamicRoutingModuleWithMaas(FareCalculatorCreator.getToyScenarioFareCalculators()));
//					controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
//							"fare/GMB.csv", "fare/light_rail_fares.csv"));
					
//				} catch (ParserConfigurationException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				} catch (SAXException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
				} catch (Exception e) {
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
	
	public static void writeTLsets(Set<Set<Id<TransitLine>>>sets,String fileLoc) {
		try {
			FileWriter fw =new FileWriter(new File(fileLoc));
			for(Set<Id<TransitLine>> set:sets) {
				String s = "";
				for(Id<TransitLine>l:set) {
					fw.append(s+l.toString());
					s= ",";
				}
				fw.append("\n");
			}
			fw.flush();
			fw.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
