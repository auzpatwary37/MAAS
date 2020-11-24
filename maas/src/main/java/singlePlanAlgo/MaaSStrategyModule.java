package singlePlanAlgo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.replanning.ReplanningContext;

import com.google.common.collect.Sets;

import maasPackagesV2.MaaSPackage;
import maasPackagesV2.MaaSPackages;
import optimizer.MultinomialLogit;
import optimizerAgent.MaaSUtil;
import transitCalculatorsWithFare.FareLink;

public class MaaSStrategyModule implements   ActivityEndEventHandler,PlanStrategyModule{

	
	public LinkedHashMap<String,MaaSPackage> maasPackages= new LinkedHashMap<>();
	private Random rnd;
	private static Logger logger = Logger.getLogger(MaaSStrategyModule.class);	
	private Set<Plan> plans = new HashSet<>();
	public MaaSStrategyModule(MaaSPackages packages){
		this.maasPackages = new LinkedHashMap<>(packages.getMassPackages());
		rnd = new Random();
	}
	
	@Override
	public void handleEvent(ActivityEndEvent event) {
		// TODO Auto-generated method stub
		
	}
	

	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		// For preparing thread 
		logger.log(Logger.Level.INFO, "Entering into replanning context in MaaSStrategyModule.");
		
	}

	@Override
	public void handlePlan(Plan plan) {
		this.plans.add(plan);
		
	}

	@Override
	public void finishReplanning() {
		// For actual execution of replanning 
		this.plans.parallelStream().forEach(plan->{
			//this.intelligentMaaSSelection(plan);
			this.randomMaaSSelection(plan);
		});
		this.plans.clear();
	}
	
	private void randomMaaSSelection(Plan plan) {
		List<String> MaaSKeys=new ArrayList<>(this.maasPackages.keySet());
		String currentMaaSPackage = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		Set<String> irreleventMaaSPackages =  (Set<String>) plan.getPerson().getAttributes().getAttribute(MaaSUtil.irreleventPlanFlag);
		if(irreleventMaaSPackages==null)irreleventMaaSPackages = new HashSet<>();
		MaaSKeys.removeAll(irreleventMaaSPackages);
		
		int ind=0;
		if(!MaaSKeys.isEmpty()) {
			if(currentMaaSPackage == null) {
				ind=rnd.nextInt(MaaSKeys.size());
			} else {
				MaaSKeys.remove(currentMaaSPackage);
				ind=rnd.nextInt(MaaSKeys.size()+1);
			}
		}
			
		if(ind == MaaSKeys.size()) {
			plan.getAttributes().removeAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		}else {
			plan.getAttributes().putAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName,MaaSKeys.get(ind));
		}
		plan.getAttributes().removeAttribute(FareLink.FareLinkAttributeName);
	}
	
	
	private void intelligentMaaSSelection(Plan plan) {
		String currentPlan = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		Map<String,Double> usedfareLinks = new HashMap<>();
		plan.getPerson().getPlans().forEach(pl->{
			Map<String,Double> fls = (Map<String, Double>) pl.getAttributes().getAttribute("FareLinks");
			//Currently we do not take score as weight. So, the weighting is equal or there is no weight on the fareLinks
			if(fls==null)//this means no fare links in this plan
				return;
			fls.entrySet().forEach(e->{
				usedfareLinks.compute(e.getKey(),(k,v)->v==null?e.getValue():v+e.getValue());
			});
		});
		Map<String,Double> scores = new LinkedHashMap<>();
		scores.put(MaaSUtil.nullMaaSPacakgeKeyName, 0.);
		for(Entry<String, MaaSPackage> pac:this.maasPackages.entrySet()) {
			Set<String> packageFareLinks = new HashSet<>(pac.getValue().getDiscounts().keySet());
			Set<String> interSet = Sets.intersection(new HashSet<>(usedfareLinks.keySet()), packageFareLinks);
			double useScore = 0;
			
			for(String s:interSet){
				useScore+=usedfareLinks.get(s);
			}
			if(useScore!=0)scores.put(pac.getKey(),useScore);
		}
		if(currentPlan!=null) {
			scores.remove(currentPlan);
		}
		//logit
		String key = new MultinomialLogit(scores).sample();
		if(key.equals(MaaSUtil.nullMaaSPacakgeKeyName)) {
			plan.getAttributes().removeAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		}else {
			plan.getAttributes().putAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName, key);
		}
		
	}
}
