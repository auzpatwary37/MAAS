package singlePlanAlgo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonMoneyEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.population.PopulationUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.inject.name.Named;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import optimizerAgent.MaaSUtil;
import transitCalculatorsWithFare.FareLink;

//TODO Talk to enoch and incorporate personId here. 

public class MaaSDiscountAndChargeHandler implements PersonMoneyEventHandler, PersonDepartureEventHandler{

	
	protected Scenario scenario;
	@Inject
	protected @Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages;
	protected final EventsManager eventManager;
	protected final Map<Id<Person>,Person> operatorMap = new HashMap<>();
	protected final Set<Id<Person>> personIdWithPlan = new HashSet<>();
	protected Map<String,Double> govSubsidyRatio = null;
	//private final Map<Id<Person>, Double> fareSaved = new HashMap<>();
	@Inject
	MaaSDiscountAndChargeHandler(final MatsimServices controler, final TransitSchedule transitSchedule,
			Map<String, FareCalculator> fareCals, TransferDiscountCalculator tdc, @Nullable @Named("SubsidyRatio")Map<String,Double> govSubsidyRatio) {
		eventManager = controler.getEvents();
		this.scenario = controler.getScenario();
		this.scenario.getPopulation().getPersons().values().forEach(p->{
			if(PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				this.operatorMap.put(p.getId(),p);
			}
		});
		this.govSubsidyRatio = govSubsidyRatio;
	}
	@Override
	public void reset(int reset){
		if(govSubsidyRatio.isEmpty()) {
			this.govSubsidyRatio = this.packages.getMassPackages().keySet().stream().collect(Collectors.toMap(k->k.toString(), k->0.));
		}
		this.operatorMap.values().forEach(p->{
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.operatorRevenueName, 0.);
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.PackageSoldKeyName, 0);
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.PackageTripKeyName, 0);
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.operatorTripKeyName, 0);
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.govSubsidyName, 0.);
		});
		personIdWithPlan.clear();
		
	}
	
	@Override
	public void handleEvent(PersonMoneyEvent event) {
		Id<Person> personId = event.getPersonId();
		if(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_PURPOSE).equals(FareLink.FareTransactionName)) {//So, this is a fare payment event
			FareLink fl = new FareLink(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_TRANSACTION_PARTNER));
			Person person = this.scenario.getPopulation().getPersons().get(personId);
			Plan plan = person.getSelectedPlan();
//			if(plan.getScore()==null) {
//				if(plan.getAttributes().getAttribute("FareLinks")==null) {
//					plan.getAttributes().putAttribute("FareLinks", new HashMap<String,Double>());
//				}
//				Map<String,Double> fareLinks = (Map<String, Double>) plan.getAttributes().getAttribute("FareLinks");
//				fareLinks.compute(fl.toString(),(k,v)->(v==null)?1:v+1);//It will keep increasing. We should update it only once
//			}
			
			double fare = Double.parseDouble(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_AMOUNT));
			double time = event.getTime(); // Obtain the time
			String chosenMaaSid = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
			double fareRevenue = -1*fare;
			double discount = 0;
			if(chosenMaaSid!=null) {
				discount = Math.min(this.packages.getMassPackages().get(chosenMaaSid).getDiscountForFareLink(fl),-1*fare);
				fareRevenue -= discount;
				this.eventManager.processEvent(new PersonMoneyEvent(time,event.getPersonId(), discount,MaaSUtil.MaaSDiscountReimbursementTransactionName,fl.toString()));//Reimbursement Event
				double fareSaved = discount;
				if(person.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.fareSavedAttrName)!=null) {
					fareSaved += (Double) person.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.fareSavedAttrName);
				}				
				person.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.fareSavedAttrName, fareSaved);
			}else {
				person.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.fareSavedAttrName, 0.); //If they didn't choose a plan, they saved nothing.
			}
			
			if(this.packages.getOperatorId(fl)!=null) {//the fare link might not be under any operators that are being optimized
				String fareLinkOperatorId = this.packages.getOperatorId(fl)+MaaSUtil.MaaSOperatorSubscript;
				this.eventManager.processEvent(new PersonMoneyEvent(time,Id.createPersonId(fareLinkOperatorId), fareRevenue, MaaSUtil.MaaSOperatorFareRevenueTransactionName,fl.toString()+"__"+event.getPersonId()));//Operator fare revenue event.
				Plan selectedOperatorPlan =  this.scenario.getPopulation().getPersons().get(Id.createPersonId(fareLinkOperatorId)).getSelectedPlan();
				this.updateRevenue(fareRevenue, selectedOperatorPlan);
				this.updateOperatorTrip(selectedOperatorPlan);
				if(discount>0)this.updatePackageTrip(selectedOperatorPlan);
			}
		}
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		Id<Person> personId = event.getPersonId();
		if(!this.scenario.getPopulation().getPersons().containsKey(personId)) {
			return; //Ignore the 'person' that is not actually a person (e.g. A bus driver) 
		}
		Person person = this.scenario.getPopulation().getPersons().get(personId);
		Plan plan = person.getSelectedPlan();
		String selectedMaaSid = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		
		if(selectedMaaSid!=null && !personIdWithPlan.contains(personId)) {
			MaaSPackage m = this.packages.getMassPackages().get(selectedMaaSid);
			double maasCost = m.getPackageCost();
			Double subsidyRatio = this.govSubsidyRatio.get(m.getId());
			if(subsidyRatio == null) subsidyRatio = 0.;
			String packageOperatorId = m.getOperatorId()+MaaSUtil.MaaSOperatorSubscript;
			this.eventManager.processEvent(new PersonMoneyEvent(event.getTime(),personId, -maasCost*(1-subsidyRatio), MaaSUtil.AgentpayForMaaSPackageTransactionName,m.getId()));//Agent buying package
			this.eventManager.processEvent(new PersonMoneyEvent(event.getTime(),Id.createPersonId(packageOperatorId), maasCost, MaaSUtil.MaaSOperatorpacakgeRevenueTransactionName,m.getId()+"__"+event.getPersonId()));//Operator earning revenue by selling package.
			
			Plan selectedOperatorPlan =  this.scenario.getPopulation().getPersons().get(Id.createPersonId(packageOperatorId)).getSelectedPlan();
			this.updateSoldPackage(selectedOperatorPlan);
			this.updateRevenue(maasCost, selectedOperatorPlan);
			this.updateSubsidy(subsidyRatio, maasCost, selectedOperatorPlan);
			plan.getAttributes().putAttribute(MaaSUtil.fareSavedAttrName, 0.);
			personIdWithPlan.add(personId);
		}
	}

	
	protected synchronized void updateRevenue(double maasCost, Plan selectedOperatorPlan) {
	
		Double oldReveneue = (Double)selectedOperatorPlan.getAttributes().getAttribute(MaaSUtil.operatorRevenueName);
		selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.operatorRevenueName, oldReveneue+maasCost);
	}
	
	protected synchronized void updateSubsidy(double subsidyRatio, double maasCost, Plan selectedOperatorPlan) {
		Double oldSubsidy = (Double)selectedOperatorPlan.getAttributes().getAttribute(MaaSUtil.govSubsidyName);
		selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.govSubsidyName, oldSubsidy+maasCost*subsidyRatio);
	}
	
	protected synchronized void updateSoldPackage(Plan selectedOperatorPlan) {
		int soldPackage = (int)selectedOperatorPlan.getAttributes().getAttribute(MaaSUtil.PackageSoldKeyName);
		soldPackage++;
		selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.PackageSoldKeyName,soldPackage);
	}
	protected synchronized void updateOperatorTrip(Plan selectedOperatorPlan) {
		selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.operatorTripKeyName, (int)selectedOperatorPlan.getAttributes().getAttribute(MaaSUtil.operatorTripKeyName)+1);
	}
	
	protected synchronized void updatePackageTrip(Plan selectedOperatorPlan) {
		selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.PackageTripKeyName, (int)selectedOperatorPlan.getAttributes().getAttribute(MaaSUtil.PackageTripKeyName)+1);
	}
}
