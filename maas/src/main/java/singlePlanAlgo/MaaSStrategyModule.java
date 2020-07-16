package singlePlanAlgo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.replanning.ReplanningContext;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import optimizerAgent.MaaSUtil;
import transitCalculatorsWithFare.FareLink;

public class MaaSStrategyModule implements   ActivityEndEventHandler,PlanStrategyModule{

	
	public LinkedHashMap<String,MaaSPackage> maasPackages= new LinkedHashMap<>();
	private Random rnd;
	private static Logger logger = Logger.getLogger(MaaSStrategyModule.class);	
	
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
		//this.intelligentMaaSSelection(plan);
		this.randomMaaSSelection(plan);
	}

	@Override
	public void finishReplanning() {
		// For actual execution of replanning 
		
	}
	
	private void randomMaaSSelection(Plan plan) {
		List<String> MaaSKeys=new ArrayList<>(this.maasPackages.keySet());
		String currentMaaSPackage = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		if(currentMaaSPackage==null) {
			plan.getAttributes().putAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName,MaaSKeys.get(rnd.nextInt(MaaSKeys.size())));
		}else {
			boolean repeat = true;
			int ind=0;
			while(repeat) {
				ind=rnd.nextInt(MaaSKeys.size()+1);
				if(ind == MaaSKeys.size() || !currentMaaSPackage.equals(MaaSKeys.get(ind))) {
					repeat = false;
				}
			}
			if(ind == MaaSKeys.size()) {
				plan.getAttributes().removeAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
			}else {
				plan.getAttributes().putAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName,MaaSKeys.get(ind));
			}
		}
	}
	
	
	private void intelligentMaaSSelection(Plan plan) {
		Map<String,Double> fareLinkUsage = new HashMap<>();
		
		plan.getPerson().getPlans().forEach(pl->{
			Map<String,Double> fls = (Map<String, Double>) pl.getAttributes().getAttribute("FareLinks");
			//Currently we do not take score as weight. So, the weighting is equal or there is no weight on the fareLinks
			if(fls==null)//this means no fare links in this plan
				return;
			fls.entrySet().forEach(e->{
				fareLinkUsage.compute(e.getKey(),(k,v)->v==null?e.getValue():Math.max(v, e.getValue()));
			});
		});
		Map<String,Double>expectedSavings = new HashMap<>();
		for(Entry<String, MaaSPackage> pac:this.maasPackages.entrySet()) {
			double savings = 0;
			for(Entry<String, Double> fl:fareLinkUsage.entrySet()){
				savings+=pac.getValue().getDiscountForFareLink(new FareLink(fl.getKey()))*fl.getValue();
			};
			expectedSavings.put(pac.getKey(), savings-pac.getValue().getPackageCost());
		}
		double maxSavings = Collections.max(expectedSavings.values());
		for(Entry<String, Double> d:expectedSavings.entrySet()) {
			if(d.getValue()>0 && Double.compare(d.getValue(), maxSavings)==0)
				plan.getAttributes().putAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName,d.getKey());
				return;
		}
		plan.getAttributes().removeAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
	}
}
