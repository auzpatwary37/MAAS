package singlePlanAlgo;

import javax.inject.Inject;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.core.replanning.selectors.BestPlanSelector;
import com.google.inject.name.Named;

import maasPackagesV2.MaaSPackages;
import optimizerAgent.MaaSUtil;

public class UnnecessaryMaaSPlanRemovalStrategy implements PlanStrategy{

private final PlanStrategy planStrategyDelegate;
	
	@Inject
	private @Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages;
	
	@Inject // The constructor must be annotated so that the framework knows which one to use.
	UnnecessaryMaaSPlanRemovalStrategy(@Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages) {
		// A PlanStrategy is something that can be applied to a Person (not a Plan).
        // It first selects one of the plans:
        //MyPlanSelector planSelector = new MyPlanSelector();
		

        // the plan selector may, at the same time, collect events:
        //eventsManager.addHandler(planSelector);

        // if you just want to select plans, you can stop here.


        // Otherwise, to do something with that plan, one needs to add modules into the strategy.  If there is at least
        // one module added here, then the plan is copied and then modified.
		this.packages=packages;
		UnnecessaryMaaSPackRemovalStrategyModule mod = new UnnecessaryMaaSPackRemovalStrategyModule(packages);
		@SuppressWarnings({ "rawtypes", "unchecked" })
		PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new BestPlanSelector());
		//PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new ExpBetaPlanSelector(config.planCalcScore()));
        builder.addStrategyModule(mod);

        planStrategyDelegate = builder.build();
	}
	
	@Override
	public void run(HasPlansAndId<Plan, Person> person) {
		this.planStrategyDelegate.run(person);
	}

	@Override
	public void init(ReplanningContext replanningContext) {
		this.planStrategyDelegate.init(replanningContext);
	}

	@Override
	public void finish() {
		this.planStrategyDelegate.finish();
	}
}
