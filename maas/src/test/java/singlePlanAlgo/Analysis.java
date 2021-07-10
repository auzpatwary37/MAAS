package singlePlanAlgo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.collections.Tuple;

import optimizerAgent.MaaSUtil;

public class Analysis {

	public static void main(String[] args) throws IOException {
//		Population outputPopulation = PopulationUtils.readPopulation("toyScenarioLarge/output_optim_platform/output_plans.xml.gz");
//		System.out.println("Total population = " + outputPopulation.getPersons().size());
//		for(Entry<Id<Person>, ? extends Person> p:new HashMap<>(outputPopulation.getPersons()).entrySet()) {
//			if(p.getValue().getSelectedPlan().getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName)==null) {
//				outputPopulation.removePerson(p.getKey());
//			}
//			
//		}
//		
//		new PopulationWriter(outputPopulation).write("toyScenarioLarge/output_optim_platform/output_maasPlans.xml");
//		double packCost = 46.78345336308337;
//		Population pop = PopulationUtils.readPopulation("toyScenarioLarge/output_optim_platform/output_maasPlans.xml");
//		double subOptimalPlans = 0;
//		double moneySaved = 0;
//		for(Person p:pop.getPersons().values()){
//			if(PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName))continue;
//			List<Double> scores = new ArrayList<>();
//			for(Plan pl:p.getPlans()) {
//				scores.add(pl.getScore());
//			}
//			double maxScore = Collections.max(scores);
//			if(p.getSelectedPlan().getScore()<maxScore) {
//				subOptimalPlans++;
//			}
//			Double fareSaved =  (Double) p.getAttributes().getAttribute("fareSaved");
//			if(fareSaved>46.78345) {
//				moneySaved++;
//			}
//		};
//		System.out.println("Total number of sub optimal plan choice = " + subOptimalPlans);
//		System.out.println("Total number of agents that saved money = " + moneySaved);output_optim_operatorPlatform_Apr11Govt/output_plans.xml.gz
		Population pp = PopulationUtils.readPopulation("new Data/core/populationHKI.xml");
		for(Entry<Id<Person>, ? extends Person> d:pp.getPersons().entrySet()) {
			if(d.getKey().toString().equals("32591.0_3.0_2")) {
				System.out.println(d.getValue().getPlans());
			}
		}
		countBeforAndAfterAct("new Data/core/populationHKI.xml","toyScenarioLarge/output_optim_noMaas_Elastic_NoPhantom_Apr14Govt/output_plans.xml.gz","test/odNetwork.xml");
	}
	
	public static void countBeforAndAfterAct(String beforePopPath, String afterPopPath,String odNetFilePath) throws IOException {
		Network odNet = NetworkUtils.readNetwork(odNetFilePath);
		int beforeAct = 0;
		int afterAct = 0;
		Map<String,Double> beforeActs = new HashMap<>();
		Map<String,Double> afterActs = new HashMap<>();
		
		Map<Id<Node>,Double>beforeLocationInfo = new HashMap<>();
		Population pop = PopulationUtils.readPopulation(beforePopPath);
		Map<String,Double> beforeTypicalDuration =calculateTypicalActDuration(pop);
		for(Person p:pop.getPersons().values()) {
			//if(!PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				for(PlanElement pe:p.getSelectedPlan().getPlanElements()) {
					if(pe instanceof Activity) {
						if(!((Activity)pe).getType().equals("pt interaction")&&!((Activity)pe).getType().equals(MaaSUtil.dummyActivityTypeForMaasOperator) ) {
							beforeAct++;
							beforeActs.compute(((Activity)pe).getType(), (k,v)->v==null?1:v+1);
							beforeLocationInfo.compute(NetworkUtils.getNearestNode(odNet, ((Activity)pe).getCoord()).getId(), (k,v)->v==null?1:v+1);
						}
					}
				}
			//}
		
		}
		Map<Id<Node>,Double>afterLocationInfo = new HashMap<>();
		pop = PopulationUtils.readPopulation(afterPopPath);
		Map<String,Double> afterTypicalDuration = calculateTypicalActDuration(pop);
		for(Person p:pop.getPersons().values()) {
			if(!PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				for(PlanElement pe:p.getSelectedPlan().getPlanElements()) {
					if(pe instanceof Activity) {
						if(!((Activity)pe).getType().equals("pt interaction")) {
							afterAct++;
							afterActs.compute(((Activity)pe).getType(), (k,v)->v==null?1:v+1);
							afterLocationInfo.compute(NetworkUtils.getNearestNode(odNet, ((Activity)pe).getCoord()).getId(), (k,v)->v==null?1:v+1);
						}
					}
				}
			}
		}
		
		FileWriter fw = new FileWriter(new File("toyScenarioLarge/output_optim_noMaas_Elastic_NoPhantom_Apr14Govt/beforeAfterActPatterns.csv"));
		fw.append("ActKey,BeforeCount,AfterCount,beforeDur,AfterDur\n");
		Set<String> acts = new HashSet<>(beforeActs.keySet());
		acts.addAll(afterActs.keySet());
		
		acts.stream().forEach(a->{
			try {
				fw.append(a+","+beforeActs.get(a)+","+afterActs.get(a)+","+beforeTypicalDuration.get(a)+","+afterTypicalDuration.get(a)+"\n");
				fw.flush();
			
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		fw.close();
		
		FileWriter fwLoc = new FileWriter(new File("toyScenarioLarge/output_optim_noMaas_Elastic_NoPhantom_Apr14Govt/beforeAfterActLocationPatterns.csv"));
		fwLoc.append("TPUSBKey,BeforeCount,AfterCount\n");
		Set<Id<Node>> tpusbs = new HashSet<>(beforeLocationInfo.keySet());
		tpusbs.addAll(afterLocationInfo.keySet());
		
		tpusbs.stream().forEach(a->{
			try {
				fwLoc.append(a+","+beforeLocationInfo.get(a)+","+afterLocationInfo.get(a)+"\n");
				fwLoc.flush();
			
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		fwLoc.close();
		
		System.out.println("Total Number of Activities before = " + beforeAct);
		System.out.println("Total Number of Activities after = " + afterAct);
	}
	
	public static Map<String,Double> calculateTypicalActDuration(Population pop){
		Map<String,Tuple<Double,Double>> actTotalDurationAndCountHolder = new HashMap<>();
		Map<String,Double> averageDuration = new HashMap<>();
		
		for(Entry<Id<Person>, ? extends Person> p:pop.getPersons().entrySet()) {
			for(PlanElement pe:p.getValue().getSelectedPlan().getPlanElements()) {
				if(pe instanceof Activity && !((Activity)pe).getType().equals(MaaSUtil.dummyActivityTypeForMaasOperator) &&!((Activity)pe).getType().equals("pt interaction") ) {
					Activity act = (Activity)pe;
					if(actTotalDurationAndCountHolder.get(act.getType())== null)actTotalDurationAndCountHolder.put(act.getType(),new Tuple<Double,Double>(0.,0.));
					Tuple<Double,Double> oldCount = actTotalDurationAndCountHolder.get(act.getType());
					double currentDuration = 60.;
					if(act.getStartTime().isDefined() && act.getEndTime().isDefined()) {
						currentDuration = act.getEndTime().seconds()-act.getStartTime().seconds(); 
					}else if(!act.getStartTime().isDefined()&& act.getEndTime().isDefined()) {
						currentDuration = act.getEndTime().seconds(); 
					}else if(act.getStartTime().isDefined()&& !act.getEndTime().isDefined()) {
						currentDuration = 27*3600-act.getStartTime().seconds(); 
					}
					actTotalDurationAndCountHolder.put(act.getType(),new Tuple<Double,Double>(oldCount.getFirst()+currentDuration,oldCount.getSecond()+1));
					double t = actTotalDurationAndCountHolder.get(act.getType()).getFirst()/actTotalDurationAndCountHolder.get(act.getType()).getSecond();
					averageDuration.put(act.getType(), t);
				}
			}
		}
		return averageDuration;
	}
	
	
	public static Map<Id<Node>,Double> getActLocationTPUSB(Population pop, Network odNet, String odNetFileName){
		if(odNet==null)odNet = NetworkUtils.readNetwork(odNetFileName);
		Map<Id<Node>,Double>locationInfo = new HashMap<>();
		for(Entry<Id<Person>, ? extends Person> p:pop.getPersons().entrySet()) {
			for(PlanElement pe:p.getValue().getSelectedPlan().getPlanElements()) {
				if(pe instanceof Activity && !((Activity)pe).getType().equals(MaaSUtil.dummyActivityTypeForMaasOperator) &&!((Activity)pe).getType().equals("pt interaction") ) {
					Activity act = (Activity)pe;
					locationInfo.compute(NetworkUtils.getNearestNode(odNet, act.getCoord()).getId(), (k,v)->v==null?1:v+1);
				}
			}
		}
		return locationInfo;
	}
	
}
