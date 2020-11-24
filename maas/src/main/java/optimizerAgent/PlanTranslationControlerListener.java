package optimizerAgent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.population.PopulationUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import maasPackagesV2.MaaSPackages;
import singlePlanAlgo.MaaSDiscountAndChargeHandlerV2;

public class PlanTranslationControlerListener implements IterationStartsListener, StartupListener, BeforeMobsimListener, AfterMobsimListener{
	
	@Inject 
	private timeBeansWrapper timeBeansWrapped;

	@Inject
	private Scenario scenario;
	
	@Inject
	private OutputDirectoryHierarchy controlerIO;
	
	@Inject
	private @Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages;
	
	public final String FileName = "maas.csv";
	public final String varFileName  = "vars.csv";
	public final String subsudyFileName = "subsidy.csv";
	private Map<Id<Person>,Person> operators = new HashMap<>();
	
	public final String customerFileName = "customer.csv";
	public final int maxPlansPerAgent = 5;
	
	@Inject
	private MaaSDiscountAndChargeHandlerV2 maasHandler;
	
	@Inject
	private EventsManager events;
	
	@Inject
	PlanTranslationControlerListener(){
	}
	
	@Override
	public void notifyStartup(StartupEvent event) {
		for(Person p:scenario.getPopulation().getPersons().values()) {
			if(PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				this.operators.put(p.getId(),p);
			}
			
		}
		events.addHandler(maasHandler);
	}
	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		
	}

	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		//save the unique plans inside a list of plans inside the person attribute
//		scenario.getPopulation().getPersons().entrySet().parallelStream().forEach(p->{
//			if(!this.operators.containsKey(p.getKey())) {
//				List<Plan> plans;
//				if((plans = (List<Plan>)p.getValue().getAttributes().getAttribute(MaaSUtil.uniqueMaaSIncludedPlanAttributeName))==null) {
//					plans = new ArrayList<>();
//					p.getValue().getAttributes().putAttribute(MaaSUtil.uniqueMaaSIncludedPlanAttributeName,plans);
//				}
//				boolean unique = true;
//				for(Plan pl: plans) {
//					if(MaaSUtil.planEquals(pl, p.getValue().getSelectedPlan())) {
//						unique = false;
//						break;
//					}
//				}
//				if((unique && ((Double)p.getValue().getAttributes().getAttribute("fareSaved"))>0)||plans.isEmpty()) {
//					plans.add(p.getValue().getSelectedPlan());
//				}
//				if(plans.size()>this.maxPlansPerAgent) {
//					MaaSUtil.sortPlan(plans);
//					plans.remove(plans.size()-1);
//				}
//			}
//		});
	}

	@Override
	public void notifyAfterMobsim(AfterMobsimEvent event) {
		scenario.getPopulation().getPersons().entrySet().parallelStream().forEach(p->{
			if(!this.operators.containsKey(p.getKey())) {
				List<Plan> plans;
				if((plans = (List<Plan>)p.getValue().getAttributes().getAttribute(MaaSUtil.uniqueMaaSIncludedPlanAttributeName))==null) {
					plans = new ArrayList<>();
					p.getValue().getAttributes().putAttribute(MaaSUtil.uniqueMaaSIncludedPlanAttributeName,plans);
				}
				boolean unique = true;
				for(Plan pl: plans) {
					if(MaaSUtil.planEquals(pl, p.getValue().getSelectedPlan())) {
						unique = false;
						break;
					}
				}
				Double fareSaved  = (Double) p.getValue().getSelectedPlan().getAttributes().getAttribute("fareSaved");
				String currentPac = (String)p.getValue().getSelectedPlan().getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
				Double packageCost = 0.;
				if(currentPac != null) packageCost = this.packages.getMassPackages().get(currentPac).getPackageCost();
				if(fareSaved == null) fareSaved = 0.;
				if(unique) {
				plans.add(p.getValue().getSelectedPlan());
				}
				if(plans.size()>this.maxPlansPerAgent) {
					MaaSUtil.sortPlan(plans);
					plans.remove(plans.size()-1);
				}
			}
		});
		
		//________________________________________________________
		String fileNameFull = controlerIO.getOutputFilename(FileName);
		String varFile = controlerIO.getOutputFilename(varFileName);
		String subsidyFileNameFull =  controlerIO.getOutputFilename(this.subsudyFileName);
		String customerFile = controlerIO.getIterationFilename(event.getIteration(), customerFileName);
		File fullFile = new File(fileNameFull);
		FileWriter fwFull = null;
		FileWriter varFw = null;
		FileWriter subsidyFw = null;
		FileWriter customerFw = null;
		try {
			fwFull = new FileWriter(fullFile,true);
			varFw = new FileWriter(new File(varFile),true);
			subsidyFw = new FileWriter(new File(subsidyFileNameFull),true);
			customerFw = new FileWriter(new File(customerFile), true);
			if(event.getIteration() == 0) {
				fwFull.append("Operator,iteartion,revenue,packageSold,pacakgeTrips,totalTrips\n");
				varFw.append("Operator,iteartion,var_name,varCurrent,varlowerLimit,varUpperLimit,revenue\n");
				subsidyFw.append("Operator,Iteration,subsidy,totalSystemTravelTime\n");
				customerFw.append("personId, packageType, fareSaved\n");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for(Person p:this.operators.values()) {
			Plan plan = p.getSelectedPlan();
			try {
				fwFull.append(p.getId().toString()+","+event.getIteration()+","+plan.getAttributes().getAttribute(MaaSUtil.operatorRevenueName)+","+plan.getAttributes().getAttribute(MaaSUtil.PackageSoldKeyName)+","+
						plan.getAttributes().getAttribute(MaaSUtil.PackageTripKeyName)+","+plan.getAttributes().getAttribute(MaaSUtil.operatorTripKeyName)+"\n");
				subsidyFw.append(p.getId()+","+event.getIteration()+","+p.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.govSubsidyName)+"\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
				
			for(Object a:plan.getAttributes().getAsMap().values()) {
				if(a instanceof VariableDetails) {
					VariableDetails var = (VariableDetails)a;
					try {
						varFw.append(p.getId().toString()+","+event.getIteration()+","+var.getVariableName()+","+var.getCurrentValue()+","+var.getLimit().getFirst()+","+var.getLimit().getSecond()+","+plan.getAttributes().getAttribute(MaaSUtil.operatorRevenueName)+"\n");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		for(Person person: scenario.getPopulation().getPersons().values()) {
			if(person.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName) != null) {
				try {
					customerFw.append(person.getId()+","+person.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName)+","+
							person.getAttributes().getAttribute(MaaSUtil.fareSavedAttrName)+"\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		
		try {
			varFw.flush();
			fwFull.flush();
			subsidyFw.flush();
			fwFull.close();
			varFw.close();
			subsidyFw.close(); 
			customerFw.flush();
			customerFw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.maasHandler.writeStat(this.controlerIO.getIterationFilename(event.getIteration(), "maasAnalysis.csv"));
	}



	

}
