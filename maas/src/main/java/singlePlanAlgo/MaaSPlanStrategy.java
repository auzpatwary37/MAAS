package singlePlanAlgo;

import javax.inject.Inject;
import javax.inject.Provider;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ChangeModeConfigGroup;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.core.replanning.modules.ChangeLegMode;
import org.matsim.core.replanning.modules.ReRoute;
import org.matsim.core.replanning.modules.TripsToLegsModule;
import org.matsim.core.replanning.selectors.ExpBetaPlanSelector;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.router.TripRouter;
import org.matsim.facilities.ActivityFacilities;

import com.google.inject.name.Named;

import maasPackagesV2.MaaSPackages;
import optimizerAgent.MaaSUtil;

public class MaaSPlanStrategy implements PlanStrategy{

	private final PlanStrategy planStrategyDelegate;
	
	@Inject
	private @Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages;
	
	@Inject // The constructor must be annotated so that the framework knows which one to use.
	MaaSPlanStrategy(Config config,Scenario scenario, EventsManager eventsManager,@Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages,
			GlobalConfigGroup globalConfigGroup, ChangeModeConfigGroup changeLegModeConfigGroup, ActivityFacilities activityFacilities, Provider<TripRouter> tripRouterProvider) {
		// A PlanStrategy is something that can be applied to a Person (not a Plan).
        // It first selects one of the plans:
        //MyPlanSelector planSelector = new MyPlanSelector();
		

        // the plan selector may, at the same time, collect events:
        //eventsManager.addHandler(planSelector);

        // if you just want to select plans, you can stop here.


        // Otherwise, to do something with that plan, one needs to add modules into the strategy.  If there is at least
        // one module added here, then the plan is copied and then modified.
		this.packages=packages;
		MaaSStrategyModule mod = new MaaSStrategyModule(packages);
		@SuppressWarnings({ "rawtypes", "unchecked" })
		PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new RandomPlanSelector());
		//PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new ExpBetaPlanSelector(config.planCalcScore()));
        builder.addStrategyModule(mod);
//      builder.addStrategyModule(new TripsToLegsModule(tripRouterProvider, globalConfigGroup));
//		builder.addStrategyModule(new ChangeLegMode(globalConfigGroup, changeLegModeConfigGroup));
		builder.addStrategyModule(new ReRoute(activityFacilities, tripRouterProvider, globalConfigGroup));
        builder.addStrategyModule(new MaaSConsistancyChecker(packages));
        // these modules may, at the same time, be events listeners (so that they can collect information):
        eventsManager.addHandler(mod);

        
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
