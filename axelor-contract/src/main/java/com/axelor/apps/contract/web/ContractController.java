/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.contract.web;

import java.time.LocalDate;
import java.util.Map;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.DurationService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.contract.db.Contract;
import com.axelor.apps.contract.db.ContractTemplate;
import com.axelor.apps.contract.db.ContractVersion;
import com.axelor.apps.contract.db.repo.ContractRepository;
import com.axelor.apps.contract.db.repo.ContractTemplateRepository;
import com.axelor.apps.contract.db.repo.ContractVersionRepository;
import com.axelor.apps.contract.service.ContractService;
import com.axelor.apps.tool.ModelTool;
import com.axelor.db.JPA;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;

@Singleton
public class ContractController {

	public void waiting(ActionRequest request, ActionResponse response) {
		Contract contract = Beans.get(ContractRepository.class)
				.find(request.getContext().asType(Contract.class).getId());
		try  {
			Beans.get(ContractService.class)
					.waitingCurrentVersion(contract, getTodayDate());
			response.setReload(true);
		} catch(Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	// TODO: move to ContractVersionController
	public void waitingNextVersion(ActionRequest request, ActionResponse response) {
		try  {
			Beans.get(ContractService.class).waitingNextVersion(
					JPA.find(
							ContractVersion.class,
							request.getContext().asType(ContractVersion.class).getId()
					).getContractNext(), getTodayDate());
			response.setReload(true);
		} catch(Exception e) {
			String flash = e.toString();
			if (e.getMessage() != null) { flash = e.getMessage(); }
			response.setError(flash);
		}
	}

	public void ongoing(ActionRequest request, ActionResponse response) {
		Contract contract = Beans.get(ContractRepository.class)
				.find(request.getContext().asType(Contract.class).getId());
		try  {
			Invoice invoice = Beans.get(ContractService.class)
					.ongoingCurrentVersion(contract, getTodayDate());
			if ( invoice == null){
				response.setReload(true);
			}else{
				response.setView ( ActionView.define( I18n.get("Invoice") ) 
						.model(Invoice.class.getName())
						.add("form", "invoice-form")
						.add("grid", "invoice-grid")
						.param("forceTitle", "true")
						.context("_showRecord", invoice.getId().toString())
						.map() );
			}
		} catch(Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	public void invoicing(ActionRequest request, ActionResponse response) {
		Contract contract = Beans.get(ContractRepository.class)
				.find(request.getContext().asType(Contract.class).getId());
		try  {
			Invoice invoice = Beans.get(ContractService.class)
					.invoicingContract(contract);
			response.setView ( ActionView.define( I18n.get("Invoice") )
					.model(Invoice.class.getName())
					.add("form", "invoice-form")
					.add("grid", "invoice-grid")
					.param("forceTitle", "true")
					.context("_showRecord", invoice.getId().toString())
					.map() );
		} catch(Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	public void terminated(ActionRequest request, ActionResponse response) {
		Contract contract = Beans.get(ContractRepository.class)
				.find(request.getContext().asType(Contract.class).getId());
		try  {
			ContractService service = Beans.get(ContractService.class);
		    service.checkCanTerminateContract(contract);
			service.terminateContract(contract, true, contract.getTerminatedDate());
			response.setReload(true);
		} catch(Exception e) {
			TraceBackService.trace(response, e);
		}
	}
	
	public void renew(ActionRequest request, ActionResponse response) {
		Contract contract = Beans.get(ContractRepository.class)
				.find(request.getContext().asType(Contract.class).getId());
		try  {
			Beans.get(ContractService.class).renewContract(contract, getTodayDate());
			response.setReload(true);
		} catch(Exception e) {
			TraceBackService.trace(response, e);
		}
	}
	

	// TODO: move to ContractVersionController
	public void activeNextVersion(ActionRequest request, ActionResponse response) {
		try  {
			ContractVersion contractVersion = request.getContext().asType(ContractVersion.class);
			Beans.get(ContractService.class)
					.activeNextVersion(JPA.find(ContractVersion.class, contractVersion.getId()).getContractNext(), getTodayDate());
			response.setView(ActionView
					.define("Contract")
					.model(Contract.class.getName())
					.add("form", "contract-form")
					.add("grid", "contract-grid")
					.context("_showRecord", contractVersion.getContract().getId()).map());
		} catch(Exception e) {
			String flash = e.toString();
			if (e.getMessage() != null) { flash = e.getMessage(); }
			response.setError(flash);
		}
	}

	public void deleteNextVersion(ActionRequest request, ActionResponse response) {
		final Contract contract = JPA.find(Contract.class, request.getContext().asType(Contract.class).getId());

		// TODO: move this code in Service
		JPA.runInTransaction(new Runnable() {

			@Override
			public void run() {
				ContractVersion version = contract.getNextVersion();
				contract.setNextVersion(null);
				Beans.get(ContractVersionRepository.class).remove(version);
				Beans.get(ContractRepository.class).save(contract);
			}
		});

		response.setReload(true);
	}

	// TODO: Move to ContractVersionService
	public void saveNextVersion(ActionRequest request, ActionResponse response) {
		final ContractVersion version = JPA.find(ContractVersion.class, request.getContext().asType(ContractVersion.class).getId());
		if(version.getContractNext() != null) { return; }

		Object xContractId = request.getContext().get("_xContractId");
		Long contractId;

		if (xContractId != null) {
			contractId = Long.valueOf(xContractId.toString());
		} else if (version.getContract() != null) {
			contractId = version.getContract().getId();
		} else {
			contractId = null;
		}

		if (contractId == null) {
			return;
		}

		// TODO: move in service
		JPA.runInTransaction(new Runnable() {
			@Override
			public void run() {
				Contract contract = JPA.find(Contract.class, contractId);
				contract.setNextVersion(version);
				Beans.get(ContractRepository.class).save(contract);
			}
		});

		response.setReload(true);
	}
	
	@Transactional
	public void copyFromTemplate(ActionRequest request, ActionResponse response){

		ContractTemplate template = ModelTool.toBean(ContractTemplate.class, request.getContext().get("contractTemplate"));
		template = Beans.get(ContractTemplateRepository.class).find(template.getId());
		
		Contract copy = Beans.get(ContractService.class).createContractFromTemplate(template);

		// TODO: move in service
		if (request.getContext().asType(Contract.class).getPartner() != null ){
			copy.setPartner(Beans.get(PartnerRepository.class).find( request.getContext().asType(Contract.class).getPartner().getId() ) );
			Beans.get(ContractRepository.class).save(copy);
		}
		response.setCanClose(true);
		
		response.setView ( ActionView.define( I18n.get("Contract") ) 
				.model(Contract.class.getName())
				.add("form", "contract-form")
				.add("grid", "contract-grid")
				.param("forceTitle", "true")
				.context("_showRecord", copy.getId().toString())
				.map() );
	}

	private LocalDate getTodayDate() {
		return Beans.get(AppBaseService.class).getTodayDate();
	}
}
