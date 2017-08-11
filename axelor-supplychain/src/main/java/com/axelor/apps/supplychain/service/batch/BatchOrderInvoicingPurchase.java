package com.axelor.apps.supplychain.service.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.supplychain.db.SupplychainBatch;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.db.IException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class BatchOrderInvoicingPurchase extends BatchOrderInvoicing {

	@Override
	protected void process() {
		SupplychainBatch supplychainBatch = batch.getSupplychainBatch();
		List<String> filterList = new ArrayList<>();
		Query<PurchaseOrder> query = Beans.get(PurchaseOrderRepository.class).all();

		if (supplychainBatch.getCompany() != null) {
			filterList.add("self.company = :company");
			query.bind("company", supplychainBatch.getCompany());
		}

		if (supplychainBatch.getSalespersonOrBuyerSet() != null
				&& !supplychainBatch.getSalespersonOrBuyerSet().isEmpty()) {
			filterList.add("self.buyerUser IN (:buyerSet)");
			query.bind("buyerSet", supplychainBatch.getSalespersonOrBuyerSet());
		}

		if (supplychainBatch.getTeam() != null) {
			filterList.add("self.buyerUser IS NOT NULL AND self.buyerUser.activeTeam = :team");
			query.bind("team", supplychainBatch.getTeam());
		}

		if (!Strings.isNullOrEmpty(supplychainBatch.getDeliveryOrReceiptState())) {
			List<Integer> receiptStateList = StringTool.getIntegerList(supplychainBatch.getDeliveryOrReceiptState());
			filterList.add("self.receiptState IN (:receiptStateList)");
			query.bind("receiptStateList", receiptStateList);
		}

		if (!Strings.isNullOrEmpty(supplychainBatch.getStatusSelect())) {
			List<Integer> statusSelectList = StringTool.getIntegerList(supplychainBatch.getStatusSelect());
			filterList.add("self.statusSelect IN (:statusSelectList)");
			query.bind("statusSelectList", statusSelectList);
		}

		if (supplychainBatch.getOrderUpToDate() != null) {
			filterList.add("self.orderDate <= :orderUpToDate");
			query.bind("orderUpToDate", supplychainBatch.getOrderUpToDate());
		}

		filterList.add("self.amountInvoiced < self.exTaxTotal");

		filterList.add("NOT EXISTS (SELECT 1 FROM Invoice invoice WHERE invoice.statusSelect != :invoiceStatusSelect "
				+ "AND (invoice.purchaseOrder = self "
				+ "OR invoice.purchaseOrder IS NULL AND EXISTS (SELECT 1 FROM invoice.invoiceLineList invoiceLine "
				+ "WHERE invoiceLine.purchaseOrderLine MEMBER OF self.purchaseOrderLineList)))");
		query.bind("invoiceStatusSelect", InvoiceRepository.STATUS_CANCELED);

		List<Long> anomalyList = Lists.newArrayList(0L);
		filterList.add("self.id NOT IN (:anomalyList)");
		query.bind("anomalyList", anomalyList);

		String filter = filterList.stream().map(item -> String.format("(%s)", item))
				.collect(Collectors.joining(" AND "));
		query.filter(filter);

		PurchaseOrderInvoiceService purchaseOrderInvoiceService = Beans.get(PurchaseOrderInvoiceService.class);

		for (List<PurchaseOrder> purchaseOrderList; !(purchaseOrderList = query.fetch(FETCH_LIMIT)).isEmpty(); JPA
				.clear()) {
			for (PurchaseOrder purchaseOrder : purchaseOrderList) {
				try {
					purchaseOrderInvoiceService.generateInvoice(purchaseOrder);
					incrementDone();
				} catch (Exception e) {
					incrementAnomaly();
					anomalyList.add(purchaseOrder.getId());
					query.bind("anomalyList", anomalyList);
					TraceBackService.trace(e, IException.INVOICE_ORIGIN, batch.getId());
					e.printStackTrace();
				}
			}
		}

	}

}
