package elasticDemand;

import java.awt.Point;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Provider;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ChangeModeConfigGroup;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.TimeAllocationMutatorConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.core.replanning.modules.ChangeLegMode;
import org.matsim.core.replanning.modules.ReRoute;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.facilities.ActivityFacilities;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import maasPackagesV2.MaaSPackages;
import optimizerAgent.MaaSUtil;

public class ActivityAdditionStrategy implements PlanStrategy{

	private final PlanStrategy planStrategyDelegate;
	
	private Map<String,Set<Coord>> activityLocationMap = new HashMap<>();
	private Map<String,Double> activityDurationMap = new HashMap<>();
	private Set<String> unmodifiableActivities= new HashSet<>();
	private Map<Coord,Id<Link>> pointToLinkIdMap = new HashMap<>();
	
	
	
	@Inject // The constructor must be annotated so that the framework knows which one to use.
	ActivityAdditionStrategy(Config config,Scenario scenario, EventsManager eventsManager,PlansConfigGroup plansConfigGroup,
			TimeAllocationMutatorConfigGroup timeAllocationMutatorConfigGroup, GlobalConfigGroup globalConfigGroup, ChangeModeConfigGroup changeLegModeConfigGroup, ActivityFacilities activityFacilities, Provider<TripRouter> tripRouterProvider) {
		// A PlanStrategy is something that can be applied to a Person (not a Plan).
        // It first selects one of the plans:
        //MyPlanSelector planSelector = new MyPlanSelector();
        

        // the plan selector may, at the same time, collect events:
        //eventsManager.addHandler(planSelector);

        // if you just want to select plans, you can stop here.
		
		for (Entry<Id<Person>, ? extends Person> p:scenario.getPopulation().getPersons().entrySet()){
			for(Plan pl:p.getValue().getPlans()){
				for(PlanElement pe:pl.getPlanElements()){
					if(pe instanceof Activity) {
						Activity act = (Activity)pe;
						if(!this.activityLocationMap.containsKey(act.getType()))this.activityLocationMap.put(act.getType(), new HashSet<>());
						Set<Coord> net = this.activityLocationMap.get(act.getType());
						if(!net.contains(act.getCoord())) {
							net.add(act.getCoord());
							this.pointToLinkIdMap.put(act.getCoord(), act.getLinkId());
						}
						if(act.getType().contains("work")||act.getType().contains("Home")||act.getType().contains("school")||act.getType().equals(MaaSUtil.dummyActivityTypeForMaasOperator)||act.getType().equals("pt interaction")) {
							this.unmodifiableActivities.add(act.getType());
						}
						
						if(!this.activityDurationMap.containsKey(act.getType())) {
							OptionalTime typicalD = config.planCalcScore().getOrCreateScoringParameters(PopulationUtils.getSubpopulation(p.getValue())).getActivityParams(act.getType()).getTypicalDuration();
							double t = 0;
							if(typicalD.isUndefined()) {
								t = 60;
							}else {
								t = typicalD.seconds();
							}
							this.activityDurationMap.put(act.getType(),t);
						}
					}
				};
			};
		};
		
		
		
		
        // Otherwise, to do something with that plan, one needs to add modules into the strategy.  If there is at least
        // one module added here, then the plan is copied and then modified.
		ActivityAdditionStrategyModule mod = new ActivityAdditionStrategyModule(this.activityLocationMap,this.activityDurationMap,this.unmodifiableActivities,scenario, this.pointToLinkIdMap);
		@SuppressWarnings({ "rawtypes", "unchecked" })
		PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new RandomPlanSelector());
		//PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new ExpBetaPlanSelector(config.planCalcScore()));
        builder.addStrategyModule(mod);
//      builder.addStrategyModule(new TripsToLegsModule(tripRouterProvider, globalConfigGroup));
        
		builder.addStrategyModule(new TimeAllocationMutatorModule(tripRouterProvider, plansConfigGroup, timeAllocationMutatorConfigGroup, globalConfigGroup ));
		builder.addStrategyModule(new TimeAllocationMutatorModule(tripRouterProvider, plansConfigGroup, timeAllocationMutatorConfigGroup, globalConfigGroup ));
		builder.addStrategyModule(new TimeAllocationMutatorModule(tripRouterProvider, plansConfigGroup, timeAllocationMutatorConfigGroup, globalConfigGroup ));
       // builder.addStrategyModule(new ChangeLegMode(globalConfigGroup, changeLegModeConfigGroup));
		builder.addStrategyModule(new ReRoute(activityFacilities, tripRouterProvider, globalConfigGroup));
        // these modules may, at the same time, be events listeners (so that they can collect information):
        //eventsManager.addHandler(mod);

        // these modules may, at the same time, be events listeners (so that they can collect information):
        //eventsManager.addHandler(mod);

        
        planStrategyDelegate = builder.build();
        
        
	}

	@Override
	public void run(HasPlansAndId<Plan, Person> person) {
		// TODO Auto-generated method stub
		this.planStrategyDelegate.run(person);
	}

	@Override
	public void init(ReplanningContext replanningContext) {
		// TODO Auto-generated method stub
		this.planStrategyDelegate.init(replanningContext);
	}

	@Override
	public void finish() {
		// TODO Auto-generated method stub
		this.planStrategyDelegate.finish();
	}


}
