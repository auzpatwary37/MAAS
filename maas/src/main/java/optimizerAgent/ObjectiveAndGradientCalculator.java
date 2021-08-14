package optimizerAgent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Map.Entry;
import java.io.*;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.*;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.*;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitTransferLink;

import maasPackagesV2.MaaSPackage;
import maasPackagesV2.MaaSPackages;
/**
 * Main idea is to write a universal objective and gradient calculator taking into account the new fareLink Operator and maas operator construct
 * @author ashraf
 *
 */
public class ObjectiveAndGradientCalculator {
	
	
	/**
	 * 
	 * @param flow
	 * @param operator
	 * @param packages
	 * @param fareCalculators
	 * @return
	 */
	public static Map<String,Double> calcRevenueObjective(SUEModelOutput flow, Set<String>operator, MaaSPackages packages, Map<String,FareCalculator> fareCalculators){
		
		
		Map<String,Double> obj = operator.stream().collect(Collectors.toMap(k->k, k->0.));
		for(String o: operator) {
			//add the revenue from package price
			for(MaaSPackage pac:packages.getMassPackagesPerOperator().get(o)) {
				if(flow.getMaaSPackageUsage().get(pac.getId())!=null) {
					obj.compute(o,(k,v)->v+flow.getMaaSPackageUsage().get(pac.getId())*pac.getPackageCost());
				}
			}
		}
		for(Entry<String,Map<String,Map<String,Double>>> timeMap:flow.getMaaSSpecificFareLinkFlow().entrySet()) {
			for(Entry<String, Map<String,Double>> maasMap:timeMap.getValue().entrySet()) {
				for(Entry<String,Double> fareLinkMap:maasMap.getValue().entrySet()) {
					FareLink fl = new FareLink(fareLinkMap.getKey());
					String fareLinkOperator = packages.getOperatorId(fl);
					String mode = fl.getMode();
					double flFlow = fareLinkMap.getValue();
					double fare = 0;
					if(fl.getType().equals(FareLink.NetworkWideFare)) {
						fare = fareCalculators.get(mode).getFares(null, null, fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					}else {
						fare = fareCalculators.get(mode).getFares(fl.getTransitRoute(), fl.getTransitLine(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					}
					if(maasMap.getKey().equals(MaaSUtil.nullMaaSPacakgeKeyName)) {// no maas 
						if(fareLinkOperator!=null && operator.contains(fareLinkOperator)) {
							double f = fare;
							obj.compute(fareLinkOperator, (k,v)->v = v+f*flFlow);
						}

					}else {
						double discount = packages.getMassPackages().get(maasMap.getKey()).getDiscountForFareLink(fl); 
						if(discount>0) {
							MaaSPackage pac = packages.getMassPackages().get(maasMap.getKey());
							String maasOperator = pac.getOperatorId();
							double rr = pac.getOperatorReimburesementRatio().get(fareLinkOperator);
							if(fareLinkOperator!=null && operator.contains(fareLinkOperator)) {
								double f = fare+(rr-1)*discount;
								obj.compute(fareLinkOperator, (k,v)->v = v+f*flFlow);
							}
							
							if(operator.contains(maasOperator)) {
								double f = -1*rr*discount;
								obj.compute(maasOperator, (k,v)->v = v+f*flFlow);
							}
						}else {
							if(fareLinkOperator!=null && operator.contains(fareLinkOperator)) {
								double f = fare;
								obj.compute(fareLinkOperator, (k,v)->v = v+f*flFlow);
								
							}

						}
					}
				}
			}
		}

		
		return obj;
	}
	
	
	
	/**
	 * 
	 * @param flow
	 * @param operator
	 * @param packages
	 * @param fareCalculators
	 * @return
	 */
	public static Map<String,Double> calcCompleteRevenueObjective(SUEModelOutput flow, Set<String>operator, MaaSPackages packages, Map<String,FareCalculator> fareCalculators){
		
		
		Map<String,Double> obj = operator.stream().collect(Collectors.toMap(k->k, k->0.));
		for(String o: operator) {
			//add the revenue from package price
			for(MaaSPackage pac:packages.getMassPackagesPerOperator().get(o)) {
				if(flow.getMaaSPackageUsage().get(pac.getId())!=null) {
					obj.compute(o,(k,v)->v+flow.getMaaSPackageUsage().get(pac.getId())*pac.getPackageCost());
				}
			}
		}
		for(Entry<String,Map<String,Map<String,Double>>> timeMap:flow.getMaaSSpecificFareLinkFlow().entrySet()) {
			for(Entry<String, Map<String,Double>> maasMap:timeMap.getValue().entrySet()) {
				for(Entry<String,Double> fareLinkMap:maasMap.getValue().entrySet()) {
					FareLink fl = new FareLink(fareLinkMap.getKey());
					String fareLinkOperator = packages.getOperatorId(fl);
					String mode = fl.getMode();
					double flFlow = fareLinkMap.getValue();
					double fare = 0;
					if(fl.getType().equals(FareLink.NetworkWideFare)) {
						fare = fareCalculators.get(mode).getFares(null, null, fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					}else {
						fare = fareCalculators.get(mode).getFares(fl.getTransitRoute(), fl.getTransitLine(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					}
					if(maasMap.getKey().equals(MaaSUtil.nullMaaSPacakgeKeyName)) {// no maas 
						if(fareLinkOperator!=null) {
							double f = fare;
							obj.compute(fareLinkOperator, (k,v)->v==null? f*flFlow:v+f*flFlow);
						}

					}else {
						double discount = packages.getMassPackages().get(maasMap.getKey()).getDiscountForFareLink(fl); 
						if(discount>0) {
							MaaSPackage pac = packages.getMassPackages().get(maasMap.getKey());
							String maasOperator = pac.getOperatorId();
							double rr = pac.getOperatorReimburesementRatio().get(fareLinkOperator);
							if(fareLinkOperator!=null ) {
								double f = fare+(rr-1)*discount;
								obj.compute(fareLinkOperator, (k,v)->v==null? f*flFlow: v+f*flFlow);
							}
							
							
							double f = -1*rr*discount;
							obj.compute(maasOperator, (k,v)->v==null? f*flFlow:v+f*flFlow);
							
						}else {
							if(fareLinkOperator!=null) {
								double f = fare;
								obj.compute(fareLinkOperator, (k,v)->v ==null? f*flFlow:v+f*flFlow);
								
							}

						}
					}
				}
			}
		}

		
		return obj;
	}
	

	/**
	 * 
	 * @param flow
	 * @param operator
	 * @param packages
	 * @param fareCalculators
	 * @return
	 */
	public static packUsageStat calcPackUsageStat(SUEModelOutput flow, MaaSPackages packages, Map<String,FareCalculator> fareCalculators){
		Map<String,Double> packagesSold = new HashMap<>();//packages Sold 
		Map<String,Double> selfPackageTrip = new HashMap<>();// package trips  (fare link operator trips where the package is owned by the fare link operator)
		Map<String,Double> crossPackageTrip = new HashMap<>();// Package trips (fare link operator trips where the package is owned by another operator)
		Map<String,Double> packageTrip = new HashMap<>();//package trips (all trips under a maas Operator)
		Map<String,Double> totalTrip = new HashMap<>();
		Map<String,Double> revenue = new HashMap<>();
		Set<String>operators = new HashSet<>();
		double autoTrip = 0;
		double act = 0;
		
		for(String o: packages.getMassPackagesPerOperator().keySet()) {
			//add the revenue from package price
			for(MaaSPackage pac:packages.getMassPackagesPerOperator().get(o)) {
				if(flow.getMaaSPackageUsage().get(pac.getId())!=null) {
					packagesSold.compute(o, (k,v)->v==null?flow.getMaaSPackageUsage().get(pac.getId()):v+flow.getMaaSPackageUsage().get(pac.getId()));
					revenue.compute(o, (k,v)->v==null?flow.getMaaSPackageUsage().get(pac.getId())*pac.getPackageCost() :v+flow.getMaaSPackageUsage().get(pac.getId())*pac.getPackageCost());
				}
			}
		}
		for(Entry<String,Map<String,Map<String,Double>>> timeMap:flow.getMaaSSpecificFareLinkFlow().entrySet()) {
			for(Entry<String, Map<String,Double>> maasMap:timeMap.getValue().entrySet()) {
				for(Entry<String,Double> fareLinkMap:maasMap.getValue().entrySet()) {
					FareLink fl = new FareLink(fareLinkMap.getKey());
					String fareLinkOperator = packages.getOperatorId(fl);
					String mode = fl.getMode();
					double flFlow = fareLinkMap.getValue();
					double fare = 0;
					if(fl.getType().equals(FareLink.NetworkWideFare)) {
						fare = fareCalculators.get(mode).getFares(null, null, fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					}else {
						fare = fareCalculators.get(mode).getFares(fl.getTransitRoute(), fl.getTransitLine(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					}
					if(maasMap.getKey().equals(MaaSUtil.nullMaaSPacakgeKeyName)) {// no maas 
						if(fareLinkOperator!=null) {
							totalTrip.compute(fareLinkOperator, (k,v)->v==null?flFlow:v+flFlow);
							double f = fare;
							revenue.compute(fareLinkOperator, (k,v)->v==null?f*flFlow:v+f*flFlow);
						}
					}else {
						double discount = packages.getMassPackages().get(maasMap.getKey()).getDiscountForFareLink(fl); 
						if(discount>0) {
							MaaSPackage pac = packages.getMassPackages().get(maasMap.getKey());
							String maasOperator = pac.getOperatorId();
							double rr = pac.getOperatorReimburesementRatio().get(fareLinkOperator);
							if(fareLinkOperator!=null) {
								selfPackageTrip.compute(fareLinkOperator, (k,v)->v==null?flFlow:v+flFlow);
								packageTrip.compute(maasOperator, (k,v)->v==null?flFlow:v+flFlow);
								totalTrip.compute(fareLinkOperator, (k,v)->v==null?flFlow:v+flFlow);
								operators.add(fareLinkOperator);
								operators.add(maasOperator);
								double f = fare;
								
								revenue.compute(fareLinkOperator, (k,v)->v==null?(f+(rr-1)*discount)*flFlow:v+(f+(rr-1)*discount)*flFlow);
								revenue.compute(maasOperator, (k,v)->v==null?-1*rr*discount*flFlow:v-1*rr*discount*flFlow);
							}
							
						}else {
							if(fareLinkOperator!=null) {
								double f = fare;
								totalTrip.compute(fareLinkOperator, (k,v)->v==null?flFlow:v+flFlow);
								revenue.compute(fareLinkOperator, (k,v)->v==null?f*flFlow:v+f*flFlow);
							}
						}
					}
				}
			}
		}
		for(String s:operators) {
			packagesSold.compute(s,(k,v)->v==null?0:v);
			selfPackageTrip.compute(s,(k,v)->v==null?0:v);
			packageTrip.compute(s,(k,v)->v==null?0:v);
			totalTrip.compute(s,(k,v)->v==null?0:v);
		}
		
		return new packUsageStat(totalTrip, selfPackageTrip,revenue,packagesSold,packageTrip);
	}
	
	/**
	 * 
	 * @param flow
	 * @param vot_car value of time car //take for the with car sub pop
	 * @param vot_transit value of time transit// take for with transit sub pop
	 * @param vot_wait value of time waiting
	 * @param vom value of money
	 * @return total system travel time in money
	 */
	public static double calcTotalSystemTravelTime(PersonPlanSueModel sue, SUEModelOutput flow, Double vot_car, Double vot_transit, Double vot_wait, Double vom) {
		double totalSystemTT = 0;
		// only link flow 
		double tsltt = 0;
		for(Entry<String,Map<Id<Link>,Map<String,Double>>> net : sue.getLinkGradient().entrySet()) {
			for(Entry<Id<Link>,Map<String,Double>> link:net.getValue().entrySet()) {
				//double lTv= ((CNLLink)sue.getNetworks().get(net.getKey()).getLinks().get(link.getKey())).getLinkTransitVolume();
				double tt = flow.getLinkTravelTime().get(net.getKey()).get(link.getKey());
				double lv = flow.getLinkVolume().get(net.getKey()).get(link.getKey());
				tsltt+=lv*tt;
			}
		}
		//only transit direct link flow 
		double tstdltt = 0;
		double tsttltt = 0;
		for(Entry<String,Map<Id<TransitLink>,Double>> trLinks:flow.getLinkTransitVolume().entrySet()) {
			for(Entry<Id<TransitLink>,Double>link:trLinks.getValue().entrySet()) {
				if(flow.getTransitDirectLinkTT().get(trLinks.getKey()).containsKey(link.getKey())){
					tstdltt+=link.getValue()*flow.getTransitDirectLinkTT().get(trLinks.getKey()).get(link.getKey());
				}else {
					tsttltt+=link.getValue()*flow.getTransitTransferLinkTT().get(trLinks.getKey()).get(link.getKey());
				}
			}
		}
		totalSystemTT = tsltt*vot_car/vom+tstdltt*vot_transit/vom+tsttltt*vot_wait/vom;
		return totalSystemTT;
	}
	
	/**
	 * 
	 * @param sue
	 * @param flow
	 * @param variables
	 * @param operators
	 * @param packages
	 * @param fareCalculators
	 * @return
	 */
	public static Map<String,Map<String,Double>> calcRevenueObjectiveGradient(PersonPlanSueModel sue, SUEModelOutput flow, Map<String,Double> variables, Map<String,Map<String,VariableDetails>> operators, MaaSPackages packages, Map<String,FareCalculator> fareCalculators) {
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		operators.keySet().forEach(o->operatorGradient.put(o, variables.keySet().stream().collect(Collectors.toMap(k->k, k->0.))));
		
                                           
		//As the fare link operator never have the right to choose the discount rate and the volume comes directly from the decision variable, the volume can never belong to a farelink operator.
		variables.entrySet().parallelStream().forEach(v->{
			String skey = v.getKey();
			String key = MaaSUtil.retrieveName(skey);
			if(key == "") key = skey;
			String pacakgeId = MaaSUtil.retrievePackageId(skey);
			MaaSPackage pac = null;
			if((pac = packages.getMassPackages().get(pacakgeId))==null) {
				System.out.print("Debug!!!");
			}
			String operatorId = packages.getMassPackages().get(pacakgeId).getOperatorId();
			Map<String,Double> optionalFareLinkOperatorGradientComponent = new HashMap<>();
			double volume = 0;
			
			if(MaaSUtil.ifFareLinkVariableDetails(skey)) {//if the variable is a fare link variable
				double flVol = 0;
				FareLink farelink = new FareLink(MaaSUtil.retrieveFareLink(skey));
				for(Entry<String, Map<String, Map<String, Double>>> timeFl:flow.getMaaSSpecificFareLinkFlow().entrySet()) flVol+=timeFl.getValue().get(pacakgeId).get(farelink.toString());//subtract from gradient
				volume=flVol*-1*packages.getMassPackages().get(pacakgeId).getOperatorReimburesementRatio().get(packages.getOperatorId(farelink));
				double flGrad = -1*flVol*(1-pac.getOperatorReimburesementRatio().get(packages.getOperatorId(farelink)));
				if(packages.getOperatorId(farelink)!=null)optionalFareLinkOperatorGradientComponent.compute(packages.getOperatorId(farelink),(k,vv)->vv==null?flGrad:vv+flGrad);
				
			}else if(MaaSUtil.ifMaaSPackageCostVariableDetails(skey)) {
				if(flow.getMaaSPackageUsage().get(pacakgeId)==null) {
					volume = 0;
				}else {
					volume = flow.getMaaSPackageUsage().get(pacakgeId);
				}
			}else if(MaaSUtil.ifMaaSTransitLinesDiscountVariableDetails(skey)||MaaSUtil.ifMaaSFareLinkClusterVariableDetails(skey)) {
				Set<String> fls = sue.getTransitLineFareLinkMap().get(key); 
				for(String ff:fls) {
					FareLink f = new FareLink(ff);
					double flvolume=0;
					for(Entry<String, Map<String, Map<String, Double>>> timeFl:flow.getMaaSSpecificFareLinkFlow().entrySet()) {
						if(timeFl.getValue().get(pacakgeId)!=null && timeFl.getValue().get(pacakgeId).get(f.toString())!=null) {
							flvolume+=timeFl.getValue().get(pacakgeId).get(f.toString());//subtract from gradient
						}
					}
//					double fare = fareCalculators.get(f.getMode()).getFares(f.getTransitRoute(), f.getTransitLine(), f.getBoardingStopFacility(), f.getAlightingStopFacility()).get(0);
					volume+=-1*flvolume*pac.getDiscountForFareLink(f)/v.getValue()*pac.getOperatorReimburesementRatio().get(packages.getOperatorId(f));
					double flGrad = -1*flvolume*(1-pac.getOperatorReimburesementRatio().get(packages.getOperatorId(f)))*pac.getDiscountForFareLink(f)/v.getValue();
					if(packages.getOperatorId(f)!=null)optionalFareLinkOperatorGradientComponent.compute(packages.getOperatorId(f),(k,vv)->vv==null?flGrad:vv+flGrad);
				}
			}else if(MaaSUtil.ifMaaSPackageFareLinkReimbursementRatioVariableDetails(skey)) {
				String fareLinkOp = MaaSUtil.retrieveFareLinkOperator(skey);
				for(FareLink fl: packages.getMassPackages().get(pacakgeId).getFareLinks().values()) {
					if(packages.getOperatorId(fl).equals(fareLinkOp)) {
						double flvolume = 0;
						for(Entry<String, Map<String, Map<String, Double>>> timeFl:flow.getMaaSSpecificFareLinkFlow().entrySet()) {
							if(timeFl.getValue().get(pacakgeId)!=null && timeFl.getValue().get(pacakgeId).get(fl.toString())!=null) {
								flvolume+=timeFl.getValue().get(pacakgeId).get(fl.toString());//subtract from gradient
							}
	//						double fare = fareCalculators.get(f.getMode()).getFares(f.getTransitRoute(), f.getTransitLine(), f.getBoardingStopFacility(), f.getAlightingStopFacility()).get(0);
							volume+=-1*flvolume*pac.getDiscountForFareLink(fl);
						}
					}
				}
				double flGrad = -1*volume; 
				optionalFareLinkOperatorGradientComponent.compute(fareLinkOp,(k,vv)->vv==null?flGrad:vv+flGrad);
			}
			double vol = volume;
			if(operatorGradient.containsKey(operatorId))operatorGradient.get(operatorId).compute(skey, (k,vv)->vv+vol);
			for(Entry<String, Double> fareLinkGrad:optionalFareLinkOperatorGradientComponent.entrySet()) {
				if(operatorGradient.containsKey(fareLinkGrad.getKey()))operatorGradient.get(fareLinkGrad.getKey()).compute(skey, (k,vv)->vv+fareLinkGrad.getValue());
			}
			//However, the first case is not true for the rest of the gradient element. As the farelink volume or maas package user volume gradient will always be dependent on the network and can be non zero.
			// the rest of the gradient component should be similar to the volume calculation. 
			final String kkk = key;
			//first maas package choice gradient
			sue.getPacakgeUserGradient().entrySet().forEach(pu->{
				if(!pu.getKey().equals(MaaSUtil.nullMaaSPacakgeKeyName)) {
					String o = packages.getMassPackages().get(pu.getKey()).getOperatorId();
					double cost = packages.getMassPackages().get(pu.getKey()).getPackageCost();
					if(operatorGradient.containsKey(o)) {
						operatorGradient.get(o).compute(skey,(k,vv)->vv+pu.getValue().get(kkk)*cost);
					}
				}
			});
			//Now fareLink gradients
			for(Entry<String,Map<String,Map<String,Map<String,Double>>>> timeFareGrad:sue.getFareLinkGradient().entrySet()){
				for(Entry<String,Map<String,Map<String,Double>>> maasFareGrad:timeFareGrad.getValue().entrySet()){
					String maasOperatorId = null;
					if(!maasFareGrad.getKey().equals(MaaSUtil.nullMaaSPacakgeKeyName)) {
						maasOperatorId = packages.getMassPackages().get(maasFareGrad.getKey()).getOperatorId();
					}
					for(Entry<String,Map<String,Double>> fareGrad:maasFareGrad.getValue().entrySet()){
						FareLink fl = new FareLink(fareGrad.getKey());
						String fareLinkOpId = packages.getOperatorId(fl); 
						double discount = 0;
						Double rr = 1.;
						double fullFare = fareCalculators.get(fl.getMode()).getFares(fl.getTransitRoute(), fl.getTransitLine(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
						if(maasOperatorId!=null) {
							discount = packages.getMassPackages().get(maasFareGrad.getKey()).getDiscountForFareLink(fl);
							rr = packages.getMassPackages().get(maasFareGrad.getKey()).getOperatorReimburesementRatio().get(fareLinkOpId);
							if(rr == null)rr = 1.0;
							//for maaspackage operator
							//each element should be -1*reimbursementRatio*discount*gradient
							if(discount>0 && operators.containsKey(maasOperatorId)) {
								double grad = fareGrad.getValue().get(key)*-1*rr*discount;
								operatorGradient.get(maasOperatorId).compute(skey, (k,vv)->(v==null)?grad:vv+grad);
							}
						}
						//for fare link operator
						//each element should be (fare-(1-reimbursementRatio)*discount)*gradient
						if(fareLinkOpId!=null && operators.containsKey(fareLinkOpId)) {
							double grad = 0;
							if(discount>0) {
								grad = fareGrad.getValue().get(key)*(fullFare-discount*(1-rr));
							}else {
								grad = fareGrad.getValue().get(key)*fullFare;
							}
							final double g =grad;
							operatorGradient.get(fareLinkOpId).compute(skey, (k,vv)->(v==null)?g:vv+g);
						}

					}
				}
			}
				
			
		});
		
				
		
		return operatorGradient;
	}
	
	public static Map<String,Map<String,Double>> calcTotalSystemTravelTimeGradient(PersonPlanSueModel sue, SUEModelOutput flow, Map<String,Double> variables, Map<String,Map<String,VariableDetails>> operators,Double vot_car, Double vot_transit, Double vot_wait, Double vom){
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		Map<String,Map<String,Double>>operatorSmGradient = new HashMap<>();
		operators.keySet().forEach(o->operatorGradient.put(o, new HashMap<>()));
		operators.keySet().forEach(o->operatorSmGradient.put(o, new HashMap<>()));
		operators.entrySet().stream().forEach(o->{
			for(Entry<String,VariableDetails> v:o.getValue().entrySet()){
			//o.getValue().entrySet().parallelStream().forEach(v->{
				String skey = v.getKey();
				String key = MaaSUtil.retrieveName(skey);
				if(key == "") key =skey;
				double totalSystemTTGrad = 0;
				// only link flow 
				double tslttGrad = 0;
				for(Entry<String,Map<Id<Link>,Map<String,Double>>> net : sue.getLinkTravelTimeGradient().entrySet()) {
					for(Entry<Id<Link>,Map<String,Double>> link:net.getValue().entrySet()) {
						if(sue.getLinkTravelTimeGradient().get(net.getKey()).get(link.getKey())==null) {
							System.out.println("Debug!!!");
						}
						double lVol = flow.getLinkVolume().get(net.getKey()).get(link.getKey());
						double ttGrad = sue.getLinkTravelTimeGradient().get(net.getKey()).get(link.getKey()).get(key);
						double lGrad = sue.getLinkGradient().get(net.getKey()).get(link.getKey()).get(key);
						double lTT = flow.getLinkTravelTime().get(net.getKey()).get(link.getKey());
						tslttGrad+=flow.getLinkVolume().get(net.getKey()).get(link.getKey())*sue.getLinkTravelTimeGradient().get(net.getKey()).get(link.getKey()).get(key)+
								sue.getLinkGradient().get(net.getKey()).get(link.getKey()).get(key)*flow.getLinkTravelTime().get(net.getKey()).get(link.getKey());
					}
				}
				//only transit direct link flow 
				double tstdlttGrad = 0;
				double tsttlttGrad = 0;
				for(Entry<String,Map<Id<TransitLink>,Double>> trLinks:flow.getLinkTransitVolume().entrySet()) {
					for(Entry<Id<TransitLink>,Double>link:trLinks.getValue().entrySet()) {
						if(flow.getTransitDirectLinkTT().get(trLinks.getKey()).containsKey(link.getKey())){
							tstdlttGrad+=link.getValue()*sue.getTrLinkTravelTimeGradient().get(trLinks.getKey()).get(link.getKey()).get(key)+
									sue.getTrLinkGradient().get(trLinks.getKey()).get(link.getKey()).get(key)*flow.getTransitDirectLinkTT().get(trLinks.getKey()).get(link.getKey());
						}else {
							tsttlttGrad+=link.getValue()*sue.getTrLinkTravelTimeGradient().get(trLinks.getKey()).get(link.getKey()).get(key)+
									sue.getTrLinkGradient().get(trLinks.getKey()).get(link.getKey()).get(key)*flow.getTransitTransferLinkTT().get(trLinks.getKey()).get(link.getKey());
						}
					}
				}
				totalSystemTTGrad = tslttGrad*vot_car/vom+tstdlttGrad*vot_transit/vom+tsttlttGrad*vot_wait/vom;
				operatorGradient.get(o.getKey()).put(skey, totalSystemTTGrad);
				operatorSmGradient.get(o.getKey()).put(key, totalSystemTTGrad);
			//});
		}
			if(o.getValue().size()!=operatorGradient.get(o.getKey()).size()) {
				System.out.println("Debug!!!");
			}
		});
		System.out.println();
		return operatorGradient;
	}
	
	
	
	/**
	 * 
	 * @param sue
	 * @param flow
	 * @param variables
	 * @param operators
	 * @param packages
	 * @param fareCalculators
	 * @return
	 */
	public static Map<String,Map<String,Double>> calcCompleteRevenueObjectiveGradient(PersonPlanSueModel sue, SUEModelOutput flow, Map<String,Double> variables, Map<String,Map<String,VariableDetails>> operators, MaaSPackages packages, Map<String,FareCalculator> fareCalculators) {
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		
		
		packages.getOPeratorList().forEach(o->operatorGradient.put(o, variables.keySet().stream().collect(Collectors.toMap(k->k, k->0.))));
		
		
                                           
		//As the fare link operator never have the right to choose the discount rate and the volume comes directly from the decision variable, the volume can never belong to a farelink operator.
		variables.entrySet().parallelStream().forEach(v->{
			String skey = v.getKey();
			String key = MaaSUtil.retrieveName(skey);
			if(key == "") key = skey;
			String pacakgeId = MaaSUtil.retrievePackageId(skey);
			MaaSPackage pac = null;
			if((pac= packages.getMassPackages().get(pacakgeId))==null) {
				System.out.print("Debug!!!");
			}
			String operatorId = packages.getMassPackages().get(pacakgeId).getOperatorId();
			Map<String,Double> optionalFareLinkOperatorGradientComponent = new HashMap<>();
			double volume = 0;
			
			if(MaaSUtil.ifFareLinkVariableDetails(skey)) {//if the variable is a fare link variable
				FareLink farelink = new FareLink(MaaSUtil.retrieveFareLink(skey));
				double flVol = 0; 
				for(Entry<String, Map<String, Map<String, Double>>> timeFl:flow.getMaaSSpecificFareLinkFlow().entrySet()) flVol+=timeFl.getValue().get(pacakgeId).get(farelink.toString());//subtract from gradient
				volume=flVol*-1*pac.getOperatorReimburesementRatio().get(packages.getOperatorId(farelink));
				double flGrad = -1*flVol*(1-pac.getOperatorReimburesementRatio().get(packages.getOperatorId(farelink)));
				if(packages.getOperatorId(farelink)!=null) {
					optionalFareLinkOperatorGradientComponent.compute(packages.getOperatorId(farelink),(k,vv)->vv==null?flGrad:vv+flGrad);
				}
				
			}else if(MaaSUtil.ifMaaSPackageCostVariableDetails(skey)) {
				if(flow.getMaaSPackageUsage().get(pacakgeId)==null) {
					volume = 0;
				}else {
					volume = flow.getMaaSPackageUsage().get(pacakgeId);
				}
			}else if(MaaSUtil.ifMaaSTransitLinesDiscountVariableDetails(skey)||MaaSUtil.ifMaaSFareLinkClusterVariableDetails(skey)) {
				Set<String> fls = sue.getTransitLineFareLinkMap().get(key); 
				for(String ff:fls) {
					FareLink f = new FareLink(ff);
					double flvolume=0;
					for(Entry<String, Map<String, Map<String, Double>>> timeFl:flow.getMaaSSpecificFareLinkFlow().entrySet()) {
						if(timeFl.getValue().get(pacakgeId)!=null && timeFl.getValue().get(pacakgeId).get(f.toString())!=null) {
							flvolume+=timeFl.getValue().get(pacakgeId).get(f.toString());//subtract from gradient
						}
					}
//					double fare = fareCalculators.get(f.getMode()).getFares(f.getTransitRoute(), f.getTransitLine(), f.getBoardingStopFacility(), f.getAlightingStopFacility()).get(0);
					volume=-1*flvolume*pac.getDiscountForFareLink(f)/v.getValue()*pac.getOperatorReimburesementRatio().get(packages.getOperatorId(f));
					double flGrad = -1*flvolume*(1-pac.getOperatorReimburesementRatio().get(packages.getOperatorId(f)))*pac.getDiscountForFareLink(f)/v.getValue();
					if(packages.getOperatorId(f)!=null) {
						String opId = packages.getOperatorId(f);
						optionalFareLinkOperatorGradientComponent.compute(packages.getOperatorId(f),(k,vv)->vv==null?flGrad:vv+flGrad);
					}
				}
			}else if(MaaSUtil.ifMaaSPackageFareLinkReimbursementRatioVariableDetails(skey)) {
				String fareLinkOp = MaaSUtil.retrieveFareLinkOperator(skey);
				for(FareLink fl: packages.getMassPackages().get(pacakgeId).getFareLinks().values()) {
					if(packages.getOperatorId(fl).equals(fareLinkOp)) {
						double flvolume = 0;
						for(Entry<String, Map<String, Map<String, Double>>> timeFl:flow.getMaaSSpecificFareLinkFlow().entrySet()) {
							if(timeFl.getValue().get(pacakgeId)!=null && timeFl.getValue().get(pacakgeId).get(fl.toString())!=null) {
								flvolume+=timeFl.getValue().get(pacakgeId).get(fl.toString());//subtract from gradient
							}
	//						double fare = fareCalculators.get(f.getMode()).getFares(f.getTransitRoute(), f.getTransitLine(), f.getBoardingStopFacility(), f.getAlightingStopFacility()).get(0);
							volume+=-1*flvolume*pac.getDiscountForFareLink(fl);
						}
					}
				}
				double flGrad = -1*volume; 
				optionalFareLinkOperatorGradientComponent.compute(fareLinkOp,(k,vv)->vv==null?flGrad:vv+flGrad);
			}
			double vol = volume;
			operatorGradient.get(operatorId).put(skey, operatorGradient.get(operatorId).get(skey)+vol);
			
			for(Entry<String, Double> fareLinkGradComp:optionalFareLinkOperatorGradientComponent.entrySet()) {
				operatorGradient.get(fareLinkGradComp.getKey()).put(skey, operatorGradient.get(fareLinkGradComp.getKey()).get(skey)+fareLinkGradComp.getValue());
			}
			
			//However, the first case is not true for the rest of the gradient element. As the farelink volume or maas package user volume gradient will always be dependent on the network and can be non zero.
			// the rest of the gradient component should be similar to the volume calculation. 
			final String kkk = key;
			//first maas package choice gradient
			sue.getPacakgeUserGradient().entrySet().forEach(pu->{
				if(!pu.getKey().equals(MaaSUtil.nullMaaSPacakgeKeyName)) {
					String o = packages.getMassPackages().get(pu.getKey()).getOperatorId();
					double cost = packages.getMassPackages().get(pu.getKey()).getPackageCost();
					operatorGradient.get(o).put(skey,operatorGradient.get(o).get(skey)+pu.getValue().get(kkk)*cost);
				}
			});
			//Now fareLink gradients
			for(Entry<String,Map<String,Map<String,Map<String,Double>>>> timeFareGrad:sue.getFareLinkGradient().entrySet()){
				for(Entry<String,Map<String,Map<String,Double>>> maasFareGrad:timeFareGrad.getValue().entrySet()){
					String maasOperatorId = null;
					if(!maasFareGrad.getKey().equals(MaaSUtil.nullMaaSPacakgeKeyName)) {
						maasOperatorId = packages.getMassPackages().get(maasFareGrad.getKey()).getOperatorId();
					}
					for(Entry<String,Map<String,Double>> fareGrad:maasFareGrad.getValue().entrySet()){
						FareLink fl = new FareLink(fareGrad.getKey());
						String fareLinkOpId = packages.getOperatorId(fl); 
						double discount = 0;
						double rr = 1;
						double fullFare = fareCalculators.get(fl.getMode()).getFares(fl.getTransitRoute(), fl.getTransitLine(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
						if(maasOperatorId!=null) {
							discount = packages.getMassPackages().get(maasFareGrad.getKey()).getDiscountForFareLink(fl);
							if(fareLinkOpId!=null)rr = packages.getMassPackages().get(maasFareGrad.getKey()).getOperatorReimburesementRatio().get(fareLinkOpId);
							//for maaspackage operator
							//each element should be -1*reimbursementRatio*discount*gradient
							if(discount>0) {
								double grad = fareGrad.getValue().get(key)*-1*rr*discount;
								operatorGradient.get(maasOperatorId).put(skey, operatorGradient.get(maasOperatorId).get(skey)+grad);
							}
						}
						//for fare link operator
						//each element should be (fare-(1-reimbursementRatio)*discount)*gradient
						if(fareLinkOpId!=null) {
							double grad = 0;
							if(discount>0) {
								grad = fareGrad.getValue().get(key)*(fullFare-discount*(1-rr));
							}else {
								grad = fareGrad.getValue().get(key)*fullFare;
							}
							final double g =grad;
							operatorGradient.get(fareLinkOpId).put(skey, operatorGradient.get(fareLinkOpId).get(skey)+g);
						}

					}
				}
			}
				
			
		});
		
				
		
		return operatorGradient;
	}
	
	public static packUsageStat fastCalculateRevenueObjectiveAndGradient(PersonPlanSueModel sue, SUEModelOutput flow, Map<String,Double> variables, Map<String,Map<String,VariableDetails>> operators, MaaSPackages packages, Map<String,FareCalculator> fareCalculators) {
		Map<String,Double> packageSold = packages.getOPeratorList().stream().collect(Collectors.toMap(k->k, k->0.));
		Map<String,Double> revenue = packages.getOPeratorList().stream().collect(Collectors.toMap(k->k, k->0.));
		Map<String,Double> selfPackageTrip = packages.getOPeratorList().stream().collect(Collectors.toMap(k->k, k->0.));
		Map<String,Double> totalTrip = packages.getOPeratorList().stream().collect(Collectors.toMap(k->k, k->0.));;
		Map<String,Double> packageTrip = packages.getOPeratorList().stream().collect(Collectors.toMap(k->k, k->0.));
		Map<String,Map<String,Double>> operatorGradient = new HashMap<>();
		packages.getOPeratorList().forEach(o->operatorGradient.put(o, variables.keySet().stream().collect(Collectors.toConcurrentMap(k->k, k->0.))));
		Map<String,Set<String>> fareLinkClusters = new HashMap<>();
		Map<String,Set<Id<TransitLine>>> tlClusters = new HashMap<>();
		variables.entrySet().stream().forEach(v->{
			if(MaaSUtil.ifMaaSFareLinkClusterVariableDetails(v.getKey())) {
				fareLinkClusters.put(v.getKey(), MaaSUtil.retrieveFareLinksIds(v.getKey()).stream().map(k->k.toString()).collect(Collectors.toSet()));
			}else if(MaaSUtil.ifMaaSTransitLinesDiscountVariableDetails(v.getKey())) {
				tlClusters.put(v.getKey(),MaaSUtil.retrieveTransitLineId(v.getKey()));
			}
		});
		
		boolean alreadyAccountedPacakgeUsage = false;
		for(Entry<String, Map<String, Map<String, Map<String, Double>>>> timeFl:sue.getFareLinkGradient().entrySet()){
			for(Entry<String, Map<String, Map<String, Double>>> maasFl:timeFl.getValue().entrySet()){
				String maasId = maasFl.getKey();
				MaaSPackage pac = packages.getMassPackages().get(maasId);
				String maasOperator = null;
				double packagePrice = 0;
				if(!maasId.equals(MaaSUtil.nullMaaSPacakgeKeyName)) {
					
					maasOperator = pac.getOperatorId();
					packagePrice = pac.getPackageCost();
				}
				String op = maasOperator;
				double packageUsage = flow.getMaaSPackageUsage().get(maasId);
				Map<String,Double> pacGrad = sue.getPacakgeUserGradient().get(maasId);
				
				if(!alreadyAccountedPacakgeUsage) {
					double p = packagePrice;
					packageSold.put(op, packageUsage);
					if(op!=null) {
						revenue.put(op, revenue.get(op)+packagePrice*packageUsage);
					}
					variables.entrySet().parallelStream().forEach(v->{
						String skey = MaaSUtil.retrieveName(v.getKey());
						if(skey == "") skey = v.getKey();
						if(MaaSUtil.ifMaaSPackageCostVariableDetails(v.getKey()) && MaaSUtil.retrievePackageId(v.getKey()).equals(maasId)) {
							if(op!=null) {
								operatorGradient.get(op).put(v.getKey(), operatorGradient.get(op).get(v.getKey())+packageUsage);
							}
						}
						if(op!=null) {
							operatorGradient.get(op).put(v.getKey(),operatorGradient.get(op).get(v.getKey())+p*pacGrad.get(skey));
						}
					});
					
				}
				
				for(Entry<String, Map<String, Double>> fareLinkGrad:maasFl.getValue().entrySet()) {
					FareLink fl = new FareLink(fareLinkGrad.getKey());
					double fare = fareCalculators.get(fl.getMode()).getFares(fl.getTransitRoute(), fl.getTransitLine(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					String fareOp = packages.getOperatorId(fl);
					double d = 0;
					double rr_ = 1;
					if(pac!=null) {
						d = pac.getDiscountForFareLink(fl);
						rr_ = pac.getOperatorReimburesementRatio().get(fareOp);
						
					}
					double discount = d;
					double rr = rr_;
					double usage = flow.getMaaSSpecificFareLinkFlow().get(timeFl.getKey()).get(maasId).get(fareLinkGrad.getKey());
					Map<String,Double> grad = sue.getFareLinkGradient().get(timeFl.getKey()).get(maasId).get(fareLinkGrad.getKey());
					
					double opRevenue = -1*discount*usage*rr;
					double fareOpRevenue = (fare-(1-rr)*discount)*usage;
					if(op!=null) {
						revenue.put(op, revenue.get(op)+opRevenue);
						packageTrip.put(op, packageTrip.get(op)+usage);
					}
					if(fareOp!=null) {
						revenue.put(fareOp, revenue.get(fareOp)+fareOpRevenue);
						totalTrip.put(fareOp, totalTrip.get(fareOp)+usage);
					}
					
					if(op!=null && fareOp!=null ) {//&& fareOp.equals(op)
						selfPackageTrip.put(fareOp, selfPackageTrip.get(fareOp)+usage);
					}
					for(Entry<String, Double> v:variables.entrySet()) {
						String skey = MaaSUtil.retrieveName(v.getKey());
						if(skey == "") skey = v.getKey();
						double opGrad = 0;
						double fareOpGrad = 0;
						if(pac!=null &&MaaSUtil.ifFareLinkVariableDetails(v.getKey()) && fl.toString().equals(MaaSUtil.retrieveFareLink(v.getKey())) &&  maasId.equals(MaaSUtil.retrievePackageId(v.getKey()))) {//the first variable dependent term
							opGrad += -1*usage*rr;
							fareOpGrad += -1*(1-rr)*usage;
						}else if(pac!=null &&MaaSUtil.ifMaaSFareLinkClusterVariableDetails(v.getKey())  && maasId.equals(MaaSUtil.retrievePackageId(v.getKey())) && fareLinkClusters.get(v.getKey()).contains(fl.toString())) {
							opGrad += -1*usage*discount*(1-rr)/v.getValue();
							fareOpGrad += -1*(1-rr)*usage*discount/v.getValue();
						}else if (pac!=null && MaaSUtil.ifMaaSTransitLinesDiscountVariableDetails(v.getKey()) && maasId.equals(MaaSUtil.retrievePackageId(v.getKey())) && tlClusters.get(v.getKey()).contains(fl.getTransitLine()) ) {
							opGrad += -1*usage*discount*(1-rr)/v.getValue();
							fareOpGrad += -1*(1-rr)*usage*discount/v.getValue();
						}else if (pac!=null && MaaSUtil.ifMaaSPackageFareLinkReimbursementRatioVariableDetails(v.getKey()) && pac.getId().equals(MaaSUtil.retrievePackageId(v.getKey())) && MaaSUtil.retrieveFareLinkOperator(v.getKey()).equals(fareOp)) {
							opGrad += -1*usage*discount;
							fareOpGrad += usage*discount;		
						}
						
						//Now the second term
//						if(MaaSUtil.ifMaaSPackageFareLinkReimbursementRatioVariableDetails(v.getKey()) && grad.get(skey)!=0) {
//							System.out.println("why the discount is not zero??");
//						}
						
						opGrad+=-1*rr*discount*grad.get(skey);
						fareOpGrad+=(fare-(1-rr)*discount)*grad.get(skey);
						
						if(op!=null) {
							operatorGradient.get(op).put(v.getKey(),operatorGradient.get(op).get(v.getKey())+opGrad);
						}
						if(fareOp!=null) {
							operatorGradient.get(fareOp).put(v.getKey(),operatorGradient.get(fareOp).get(v.getKey())+fareOpGrad);
						}
//						if(pac!=null && MaaSUtil.ifMaaSPackageFareLinkReimbursementRatioVariableDetails(v.getKey()) && operatorGradient.get(op).get(v.getKey())+operatorGradient.get(fareOp).get(v.getKey())>0.00001) {
//							System.out.println( operatorGradient.get(op).get(v.getKey())+operatorGradient.get(fareOp).get(v.getKey()) );
//						}
						
					}
					
					
				}
				
			}
			alreadyAccountedPacakgeUsage = true;
		}
		packUsageStat usage = new packUsageStat(totalTrip, selfPackageTrip, revenue, packageSold, packageTrip);
		usage.setGrad(operatorGradient);
		usage.setObjective(revenue);
		return usage;
	}
	
	public static Tuple<Map<String,Map<String,Double>>,Double> calcCompleteTotalSystemTravelTimeGradient(PersonPlanSueModel sue, SUEModelOutput flow, Map<String,Double> variables, Map<String,Map<String,VariableDetails>> operators,Double vot_car, Double vot_transit, Double vot_wait, Double vom){
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		Map<String,Map<String,Double>>operatorSmGradient = new HashMap<>();
		operatorGradient.put("Govt", new HashMap<>());
		operatorSmGradient.put("Govt", new HashMap<>());
		double tsltt = 0;
		double tstdltt = 0;
		double tsttltt = 0;
		double totalSystemTT = 0;
		for(Entry<String, Double> v:variables.entrySet()){
			//o.getValue().entrySet().parallelStream().forEach(v->{
				String skey = v.getKey();
				String key = MaaSUtil.retrieveName(skey);
				if(key == "") key =skey;
				double totalSystemTTGrad = 0;
				// only link flow 
				double tslttGrad = 0;
				for(Entry<String,Map<Id<Link>,Map<String,Double>>> net : sue.getLinkTravelTimeGradient().entrySet()) {
					for(Entry<Id<Link>,Map<String,Double>> link:net.getValue().entrySet()) {
						if(sue.getLinkTravelTimeGradient().get(net.getKey()).get(link.getKey())==null) {
							System.out.println("Debug!!!");
						}
						double lVol = flow.getLinkVolume().get(net.getKey()).get(link.getKey());
						double ttGrad = sue.getLinkTravelTimeGradient().get(net.getKey()).get(link.getKey()).get(key);
						double lGrad = sue.getLinkGradient().get(net.getKey()).get(link.getKey()).get(key);
						double lTT = flow.getLinkTravelTime().get(net.getKey()).get(link.getKey());
						tslttGrad+=flow.getLinkVolume().get(net.getKey()).get(link.getKey())*sue.getLinkTravelTimeGradient().get(net.getKey()).get(link.getKey()).get(key)+
								sue.getLinkGradient().get(net.getKey()).get(link.getKey()).get(key)*flow.getLinkTravelTime().get(net.getKey()).get(link.getKey());
						tsltt+=lVol*lTT;
					}
				}
				//only transit direct link flow 
				double tstdlttGrad = 0;
				double tsttlttGrad = 0;
				for(Entry<String,Map<Id<TransitLink>,Double>> trLinks:flow.getLinkTransitVolume().entrySet()) {
					for(Entry<Id<TransitLink>,Double>link:trLinks.getValue().entrySet()) {
						if(flow.getTransitDirectLinkTT().get(trLinks.getKey()).containsKey(link.getKey())){
							tstdlttGrad+=link.getValue()*sue.getTrLinkTravelTimeGradient().get(trLinks.getKey()).get(link.getKey()).get(key)+
									sue.getTrLinkGradient().get(trLinks.getKey()).get(link.getKey()).get(key)*flow.getTransitDirectLinkTT().get(trLinks.getKey()).get(link.getKey());
							tstdltt+=link.getValue()*flow.getTransitDirectLinkTT().get(trLinks.getKey()).get(link.getKey());
						}else {
							tsttlttGrad+=link.getValue()*sue.getTrLinkTravelTimeGradient().get(trLinks.getKey()).get(link.getKey()).get(key)+
									sue.getTrLinkGradient().get(trLinks.getKey()).get(link.getKey()).get(key)*flow.getTransitTransferLinkTT().get(trLinks.getKey()).get(link.getKey());
							tsttltt+=link.getValue()*flow.getTransitTransferLinkTT().get(trLinks.getKey()).get(link.getKey());
						}
					}
				}
				totalSystemTTGrad = tslttGrad*vot_car/vom+tstdlttGrad*vot_transit/vom+tsttlttGrad*vot_wait/vom;
				totalSystemTT = tsltt*vot_car/vom+tstdltt*vot_transit/vom+tsttltt*vot_wait/vom;
				operatorGradient.get("Govt").put(skey, totalSystemTTGrad);
				operatorSmGradient.get("Govt").put(key, totalSystemTTGrad);
			//});
		}
		
		return new Tuple<Map<String,Map<String,Double>>,Double>(operatorGradient,totalSystemTT);
	}
	
	
	public static Tuple<Map<String,Double>,Double> calcTotalSystemUtilityGradientAndObjective(PersonPlanSueModel model) {
		Map<String,Double> totalSystemUtilityGrad = new HashMap<>();
		double tsU = 0;
		for(Entry<String, Map<String, Double>> probGrad:model.getPlanProbabilityGradient().entrySet()){
			for(Entry<String, Double> var:probGrad.getValue().entrySet()) {
				double gc =model.getPlanProbability().get(probGrad.getKey())*model.getPlanUtilityGradient().get(probGrad.getKey()).get(var.getKey())
						+var.getValue()*model.getPlanUtility().get(probGrad.getKey());
				totalSystemUtilityGrad.compute(var.getKey(), (k,v)->v==null?gc:v+gc);	
			}
			
			double p = model.getPlanProbability().get(probGrad.getKey());
			double u = model.getPlanUtility().get(probGrad.getKey());
//			if(p<0) {
//				System.out.println("Debug Here!!!");
//			}
//			if(u<0) {
//				System.out.println("Utility is negative!!!");
//			}
			tsU+=p*u;
		}
		
		
		
		return new Tuple<Map<String,Double>,Double>(totalSystemUtilityGrad,tsU);
	}
	
	public static Tuple<Map<String,Double>,Double> calcTotalSystemEMUtilityGradientAndObjective(PersonPlanSueModel model) {
		Map<String,Double> totalSystemUtilityGrad = new HashMap<>();
		double tsU = 0;	
		for(Entry<String, Map<String, Double>> probGrad:model.getPlanProbabilityGradient().entrySet()){
			for(Entry<String, Double> var:probGrad.getValue().entrySet()) {
				double gc =model.getPlanProbability().get(probGrad.getKey())*model.getPlanUtilityGradient().get(probGrad.getKey()).get(var.getKey());
				totalSystemUtilityGrad.compute(var.getKey(), (k,v)->v==null?gc:v+gc);
			}
		}
		for(List<Plan> pls:model.getFeasibleplans().values()) {
			double sum = 0;
			List<Double> utils = new ArrayList<>();
			for(Plan pl:pls) {
				SimpleTranslatedPlan trPlan = (SimpleTranslatedPlan) pl.getAttributes().getAttribute(SimpleTranslatedPlan.SimplePlanAttributeName);
				String planId = trPlan.getPlanKey();
				utils.add(model.getPlanUtility().get(planId));
			}
			Double max = Collections.max(utils);
			for(Double d:utils) {
				sum+=Math.exp(d-max);
			}
			tsU+=max+Math.log(sum);

		}
		return new Tuple<Map<String,Double>,Double>(totalSystemUtilityGrad,tsU);
	}
	
	public static void measureAverreageTLWaitingTime(PersonPlanSueModel model, SUEModelOutput flow, String fileLoc) {
		Map<Id<TransitLine>,Integer> occurance = new HashMap<>();
		Map<Id<TransitLine>,Double> waitTime = new HashMap<>();
		for(Entry<String,Map<Id<TransitLink>,Double>>timeMap:flow.getTransitTransferLinkTT().entrySet()) {
			for(Entry<Id<TransitLink>,Double> waitTimeMap:timeMap.getValue().entrySet()) {
				CNLTransitTransferLink link  = ((CNLTransitTransferLink)model.getTransitLinks().get(timeMap.getKey()).get(waitTimeMap.getKey()));
				if(link.getNextdLink()!=null) {
					Id<TransitLine> lineId = Id.create(link.getNextdLink().getLineId(),TransitLine.class);
					occurance.compute(lineId,(k,v)->v==null?1:v+1);
					waitTime.compute(lineId, (k,v)->v==null?waitTimeMap.getValue():(v*(occurance.get(lineId)-1)+waitTimeMap.getValue())/occurance.get(lineId));
				}
			}
		}
		try {
			FileWriter fw = new FileWriter(new File(fileLoc));
			for(Entry<Id<TransitLine>,Double> e:waitTime.entrySet()) {
				fw.append(e.getKey().toString()+","+e.getValue()+"\n");
			}
			fw.flush();
			fw.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	public static Map<String,Double> readSimpleMap(String fileLoc,boolean ifHeader){
		Map<String,Double> map = new HashMap<>();
		
		try {
			BufferedReader bf = new BufferedReader(new FileReader(new File(fileLoc)));
			if(ifHeader)bf.readLine();
			String line = null;
			while((line = bf.readLine())!=null) {
				map.put(line.split(",")[0], Double.parseDouble(line.split(",")[1]));
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		return map;
	}
	
	/**
	 * 
	 * @param gradients must contain all variables for all in each operator
	 * @param objective operator objective
	 * @param targets operator target
	 * @return first term grad and second term objective
	 */
	public static Tuple<Map<String,Double>,Map<String,Double>> calcBreakEvenGradient(Map<String,Map<String,Double>>gradients, Map<String,Double>objective,Map<String,Double>targets){
		Map<String,Double> outGrad = new HashMap<>();
		Map<String,Double> outObj = new HashMap<>();
		double m = 10000000;// a large number
		Set<String> gradKeys = new HashSet<>();
		gradients.entrySet().forEach(g->gradKeys.addAll(g.getValue().keySet()));
		objective.entrySet().forEach(o->{
			outObj.put(o.getKey(),o.getValue()-targets.get(o.getKey()));
			gradKeys.forEach(g->outGrad.compute(g,(k,v)-> v==null?-1*outObj.get(o.getKey())*gradients.get(o.getKey()).get(k)*m:v+outObj.get(o.getKey())*-1*m*gradients.get(o.getKey()).get(k)));
		});
		return new Tuple<>(outGrad,outObj);
	}

	/**
	 * 
	 * @param gradients must contain all variables for all in each operator
	 * @param objective operator objective
	 * @param targets operator target
	 * @return first term grad and second term objective
	 */
	public static Tuple<Map<String,Double>,Map<String,Double>> calcOptimizedBreakEvenGradient(Map<String,Map<String,Double>>gradients, Map<String,Double>objective,Map<String,Double>targets,String opToOptimize){
		Map<String,Double> outGrad = new HashMap<>();
		Map<String,Double> outObj = new HashMap<>();
		double m = 1;// a large number
		Set<String> gradKeys = new HashSet<>();
		gradients.entrySet().forEach(g->gradKeys.addAll(g.getValue().keySet()));
		objective.entrySet().forEach(o->{
			if(!o.getKey().equals(opToOptimize)) {
				outObj.put(o.getKey(),o.getValue()-targets.get(o.getKey()));
				gradKeys.forEach(g->outGrad.compute(g,(k,v)-> v==null?outObj.get(o.getKey())*-1*gradients.get(o.getKey()).get(k)*m:v+outObj.get(o.getKey())*-1*m*gradients.get(o.getKey()).get(k)));
			}else {
				outObj.put(o.getKey(),o.getValue());
				//gradKeys.forEach(g->outGrad.compute(g,(k,v)-> v==null?gradients.get(o.getKey()).get(k)*outObj.get(o.getKey()):v+outObj.get(o.getKey())*gradients.get(o.getKey()).get(k)));
				gradKeys.forEach(g->outGrad.compute(g,(k,v)-> v==null?gradients.get(o.getKey()).get(k):v+gradients.get(o.getKey()).get(k)));
			}
		});
		return new Tuple<>(outGrad,outObj);
	}
	
}



