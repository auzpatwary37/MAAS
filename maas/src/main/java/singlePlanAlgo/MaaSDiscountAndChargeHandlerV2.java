package singlePlanAlgo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.io.*;

import javax.inject.Inject;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonMoneyEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.population.PopulationUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import com.google.inject.name.Named;


import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import optimizerAgent.MaaSUtil;
import transitCalculatorsWithFare.FareLink;

import maasPackagesV2.MaaSPackages;
import maasPackagesV2.MaaSPackage;
/**
 * This will be a universal package cost and discount handler
 * @author ashraf
 *
 */
public class MaaSDiscountAndChargeHandlerV2 implements PersonMoneyEventHandler, PersonDepartureEventHandler{

	
	protected Scenario scenario;
	@Inject
	protected @Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages;
	protected final EventsManager eventManager;
	protected final Map<Id<Person>,Person> operatorMap = new HashMap<>();
	protected final Set<Id<Person>> personIdWithPlan = new HashSet<>();
	protected Map<String,Double> operatorReveneue = new ConcurrentHashMap<>();
	protected Map<String,Double> operatorTrips = new ConcurrentHashMap<>();
	protected Map<String,Double> operatorMaaSTrips = new ConcurrentHashMap<>();
	protected Map<String,Double> operatorSelfMaaSTrips = new ConcurrentHashMap<>();
	protected Map<String,Double> discountLoss = new ConcurrentHashMap<>();
	protected Map<String,Double> operatorPackageSold = new ConcurrentHashMap<>();
	
	@Inject
	MaaSDiscountAndChargeHandlerV2(final MatsimServices controler, final TransitSchedule transitSchedule,
			Map<String, FareCalculator> fareCals, TransferDiscountCalculator tdc) {
		eventManager = controler.getEvents();
		this.scenario = controler.getScenario();
		this.scenario.getPopulation().getPersons().values().forEach(p->{
			if(PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				this.operatorMap.put(p.getId(),p);
			}
		});
	}
	
	@Override
	public void reset(int reset){
		this.operatorReveneue.clear();
		this.operatorTrips.clear();
		this.operatorMaaSTrips.clear();
		this.operatorSelfMaaSTrips.clear();
		this.operatorPackageSold.clear();
		this.discountLoss.clear();
		this.operatorMap.values().forEach(p->{
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.operatorRevenueName, 0.);
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.PackageSoldKeyName, 0);
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.PackageTripKeyName, 0);
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.SelfPackageTripKeyName, 0);
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.operatorTripKeyName, 0);
			p.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.govSubsidyName, 0.);
		});
		personIdWithPlan.clear();
		
	}
	
	@Override
	public void handleEvent(PersonMoneyEvent event) {//Here we catch the fare payment event 
		//Total three transaction happens here.
		//1. the farelink operator get the discounted fare. The discount amount is returned to the agents. 
		//2. The platform pays the discount * reimbursement ratio to the farelink operator. 
		// this works for platform, operator platform, operator everyone. 
		Id<Person> personId = event.getPersonId();
		if(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_PURPOSE).equals(FareLink.FareTransactionName)) {//So, this is a fare payment event
			FareLink fl = new FareLink(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_TRANSACTION_PARTNER));
			Person person = this.scenario.getPopulation().getPersons().get(personId);
			Plan plan = person.getSelectedPlan();
			
			double fare = Double.parseDouble(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_AMOUNT));
			double time = event.getTime(); // Obtain the time
			String chosenMaaSid = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
			double fareRevenue = -1*fare;
			double discount = 0;
			String fareLinkOperator = "unknown";
			if(chosenMaaSid!=null) {//event one only happens if a maas package is chosen
				MaaSPackage pac = this.packages.getMassPackages().get(chosenMaaSid);
				discount = Math.min(pac.getDiscountForFareLink(fl),-1*fare);
				if(discount>0) {
					fareRevenue -= discount;
					this.eventManager.processEvent(new PersonMoneyEvent(time,event.getPersonId(), discount,MaaSUtil.MaaSDiscountReimbursementTransactionName,fl.toString()));//Agent Reimbursement Event
					double fareSaved = discount;
					if(person.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.fareSavedAttrName)!=null) {
						fareSaved += (Double) person.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.fareSavedAttrName);
					}				
					plan.getAttributes().putAttribute(MaaSUtil.fareSavedAttrName, fareSaved);
					
					fareLinkOperator = this.packages.getOperatorId(fl);
					String MaaSOperator = pac.getOperatorId();
					double rr = pac.getOperatorReimburesementRatio().get(fareLinkOperator);
					//discount reimbursement to farelink operator only happens if there is a discount. two events maas operator looses and farelink operator gains the discount times reimbursement ratio
					if(!MaaSOperator.equals("unknown") && this.operatorMap.containsKey(Id.createPersonId(MaaSOperator))) {
						this.eventManager.processEvent(new PersonMoneyEvent(time,Id.createPersonId(MaaSOperator), -1*discount*rr, MaaSUtil.maasOperatorToFareLinkOperatorReimbursementTransactionName,fl.toString()+"__"+event.getPersonId()));//Operator fare revenue event.
					
						Plan selectedPlan = this.operatorMap.get(Id.createPersonId(MaaSOperator)).getSelectedPlan();
						this.updateRevenue(-1*discount*rr, selectedPlan);
						this.updatePackageTrip(selectedPlan);
					}
					double f = -1*discount*rr;
					this.operatorReveneue.compute(MaaSOperator, (k, v) -> (v == null) ? f : v+f);
					this.operatorMaaSTrips.compute(MaaSOperator, (k,v)->(v==null)?1:v+1);
					this.discountLoss.compute(MaaSOperator, (k, v) -> (v == null) ? -1*f : v-f);
					
					if(!fareLinkOperator.equals("unknown") && this.operatorMap.containsKey(Id.createPersonId(fareLinkOperator))) {
						this.eventManager.processEvent(new PersonMoneyEvent(time,Id.createPersonId(fareLinkOperator), discount*rr, MaaSUtil.fareLinkOperatorReimbursementTransactionName,fl.toString()+"__"+event.getPersonId()));//Operator fare revenue event.
						Plan selectedPlan = this.operatorMap.get(Id.createPersonId(fareLinkOperator)).getSelectedPlan();
						this.updateRevenue(discount*rr, selectedPlan);
					}
					this.operatorReveneue.compute(fareLinkOperator, (k, v) -> (v == null) ? -1*f : v-f);
					
				}
				
			}else {
				person.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.fareSavedAttrName, 0.); //If they didn't choose a plan, they saved nothing.
				fareLinkOperator = this.packages.getOperatorId(fl);
				if(fareLinkOperator == null)fareLinkOperator = "unknown";
			}
			if(!fareLinkOperator.equals("unknown") && this.operatorMap.containsKey(Id.createPersonId(fareLinkOperator))) {
				this.eventManager.processEvent(new PersonMoneyEvent(time,Id.createPersonId(fareLinkOperator), fareRevenue, MaaSUtil.MaaSOperatorFareRevenueTransactionName,fl.toString()+"__"+event.getPersonId()));//Operator fare revenue event.
				Plan selectedPlan = this.operatorMap.get(Id.createPersonId(fareLinkOperator)).getSelectedPlan();
				this.updateRevenue(fareRevenue, selectedPlan);
				this.updateOperatorTrip(selectedPlan);
				if(discount>0)this.updateSelfPackageTrip(selectedPlan);
			}
			final double f = fareRevenue;
			this.operatorReveneue.compute(fareLinkOperator, (k, v) -> (v == null) ? f : v+f);
			this.operatorTrips.compute(fareLinkOperator, (k,v)->(v==null)?1:v+1);
			if(discount>0)this.operatorSelfMaaSTrips.compute(fareLinkOperator, (k,v)->(v==null)?1:v+1);
			
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
			
			String packageOperatorId = m.getOperatorId();
			this.eventManager.processEvent(new PersonMoneyEvent(event.getTime(),personId, -maasCost, MaaSUtil.AgentpayForMaaSPackageTransactionName,m.getId()));//Agent buying package
			this.eventManager.processEvent(new PersonMoneyEvent(event.getTime(),Id.createPersonId(packageOperatorId), maasCost, MaaSUtil.MaaSOperatorpacakgeRevenueTransactionName,m.getId()+"__"+event.getPersonId()));//Operator earning revenue by selling package.
			
			Plan selectedOperatorPlan =  this.scenario.getPopulation().getPersons().get(Id.createPersonId(packageOperatorId)).getSelectedPlan();
			this.updateSoldPackage(selectedOperatorPlan);
			this.updateRevenue(maasCost, selectedOperatorPlan);
			this.operatorReveneue.compute(m.getOperatorId(), (k,v)->(v==null)?maasCost:v+maasCost);
			this.operatorPackageSold.compute(m.getOperatorId(), (k,v)->(v==null)?1:v+1);
			plan.getAttributes().putAttribute(MaaSUtil.fareSavedAttrName, 0.);
			personIdWithPlan.add(personId);
		}
	}

	/**
	 * 
	 * @param fileLoc
	 */
	public void writeStat(String fileLoc) {
		Set<String> operators = this.operatorReveneue.keySet();
		try {
			FileWriter fw = new FileWriter(new File(fileLoc));
			fw.append("Operator,Revenue,Trips,Self Package Trips, Package Trips, Package Sold, Discount Loss\n");//header
			String s = ",";
			for(String o:operators) {
				fw.append(o+s+this.operatorReveneue.get(o)+s+this.operatorTrips.get(o)+s+this.operatorSelfMaaSTrips.get(o)+s+this.operatorMaaSTrips.get(o)+s+this.operatorPackageSold.get(o)+s+this.discountLoss.get(o)+"\n");
			}
			fw.flush();
			fw.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	
	protected synchronized void updateRevenue(double maasCost, Plan selectedOperatorPlan) {
	
		Double oldReveneue = (Double)selectedOperatorPlan.getAttributes().getAttribute(MaaSUtil.operatorRevenueName);
		selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.operatorRevenueName, oldReveneue+maasCost);
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
	
	protected synchronized void updateSelfPackageTrip(Plan selectedOperatorPlan) {
		selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.SelfPackageTripKeyName, (int)selectedOperatorPlan.getAttributes().getAttribute(MaaSUtil.SelfPackageTripKeyName)+1);
	}
}
