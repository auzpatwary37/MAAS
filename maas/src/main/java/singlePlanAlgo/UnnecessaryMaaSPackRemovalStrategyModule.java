package singlePlanAlgo;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.replanning.ReplanningContext;

import maasPackagesV2.MaaSPackages;
import optimizerAgent.MaaSUtil;

public class UnnecessaryMaaSPackRemovalStrategyModule implements PlanStrategyModule{
	private List<Plan> plans;
	private MaaSPackages packages;
	
	public UnnecessaryMaaSPackRemovalStrategyModule(MaaSPackages pacakges) {
		this.packages = pacakges;
	}
	

	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		// TODO Auto-generated method stub
		plans = new ArrayList<>();
	}

	@Override
	public void handlePlan(Plan plan) {
		plans.add(plan);
	}

	@Override
	public void finishReplanning() {
		for(Plan pl:plans) {
		//plans.parallelStream().forEach(pl->{
			if(pl.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName)!=null) {
				double packageCost = this.packages.getMassPackages().get(pl.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName)).getPackageCost();
				double fareSavings = (double) pl.getAttributes().getAttribute(MaaSUtil.fareSavedAttrName);
				if(fareSavings<packageCost) {
					pl.getAttributes().removeAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
					pl.getPerson().getAttributes().putAttribute(MaaSUtil.fareSavedAttrName, 0.);
				}
		
			}
		//});
		}
	}

}
