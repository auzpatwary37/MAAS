package singlePlanAlgo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.replanning.ReplanningContext;

import com.google.common.collect.Sets;

import maasPackagesV2.MaaSPackage;
import maasPackagesV2.MaaSPackages;
import optimizerAgent.MaaSUtil;
import transitCalculatorsWithFare.FareLink;

public class MaaSConsistancyChecker implements PlanStrategyModule{
	private List<Plan>planHolder = new ArrayList<>();
	private MaaSPackages packages;
	
	public MaaSConsistancyChecker(MaaSPackages packages) {
		this.packages = packages;
	}
	
	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		planHolder.clear();
	}

	@Override
	public void handlePlan(Plan plan) {
		planHolder.add(plan);
	}

	@Override
	public void finishReplanning() {
		// TODO Auto-generated method stub
	//	this.planHolder.parallelStream().forEach(pl->this.checkAndReplaceIrreleventMaaSPackage(pl));
		for(Plan pl:this.planHolder)this.checkAndReplaceIrreleventMaaSPackage(pl);
	}
	private void checkAndReplaceIrreleventMaaSPackage(Plan plan) {
		List<FareLink> originalfareLinks = (List<FareLink>)plan.getAttributes().getAttribute("fareLink");
		Set<String> fareLinks = new HashSet<>();
		if(originalfareLinks!=null)originalfareLinks.forEach(fl->fareLinks.add(fl.toString()));
		String packId = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
//		if(packId.equals("bus")) {
//			fareLinks.forEach(a-> {
//				if(a.contains("tram")) {
//					System.out.println();
//				}
//				});
//			}
		
		if(packId!=null) {
			MaaSPackage pack = this.packages.getMassPackages().get(packId);
		
			if(Sets.intersection(fareLinks, pack.getDiscounts().keySet()).size()==0) {
				plan.getAttributes().removeAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
				Set<String> irrelevantPackages = (Set<String>) plan.getPerson().getAttributes().getAttribute(MaaSUtil.irreleventPlanFlag);
				if(irrelevantPackages == null) {
					irrelevantPackages = new HashSet<>();
					plan.getPerson().getAttributes().putAttribute(MaaSUtil.irreleventPlanFlag, irrelevantPackages);
				}
				irrelevantPackages.add(pack.getId());
//				if(irrelevantPackages.size()==this.packages.getMassPackages().size()) {
//					System.out.println();
//				}
			}
		}
	}

}
