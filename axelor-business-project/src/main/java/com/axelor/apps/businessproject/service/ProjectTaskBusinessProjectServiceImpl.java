/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.businessproject.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.AppBusinessProject;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.FrequencyRepository;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.FrequencyService;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.TaskTemplate;
import com.axelor.apps.project.db.TeamTaskCategory;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.project.service.ProjectTaskServiceImpl;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProjectTaskBusinessProjectServiceImpl extends ProjectTaskServiceImpl
    implements ProjectTaskBusinessProjectService {

  private PriceListLineRepository priceListLineRepo;
  private PriceListService priceListService;
  private PartnerPriceListService partnerPriceListService;
  private ProductCompanyService productCompanyService;

  @Inject
  public ProjectTaskBusinessProjectServiceImpl(
      ProjectTaskRepository projectTaskRepo,
      FrequencyRepository frequencyRepo,
      FrequencyService frequencyService,
      AppBaseService appBaseService,
      PriceListLineRepository priceListLineRepo,
      PriceListService priceListService,
      ProductCompanyService productCompanyService,
      PartnerPriceListService partnerPriceListService) {
    super(projectTaskRepo, frequencyRepo, frequencyService, appBaseService);
    this.priceListLineRepo = priceListLineRepo;
    this.priceListService = priceListService;
    this.partnerPriceListService = partnerPriceListService;
    this.productCompanyService = productCompanyService;
  }

  @Override
  public ProjectTask create(SaleOrderLine saleOrderLine, Project project, User assignedTo)
      throws AxelorException {
    ProjectTask task = create(saleOrderLine.getFullName() + "_task", project, assignedTo);
    task.setProduct(saleOrderLine.getProduct());
    task.setUnit(saleOrderLine.getUnit());
    task.setCurrency(project.getClientPartner().getCurrency());
    if (project.getPriceList() != null) {
      PriceListLine line =
          priceListLineRepo.findByPriceListAndProduct(
              project.getPriceList(), saleOrderLine.getProduct());
      if (line != null) {
        task.setUnitPrice(line.getAmount());
      }
    }
    if (task.getUnitPrice() == null) {
      Company company =
          saleOrderLine.getSaleOrder() != null ? saleOrderLine.getSaleOrder().getCompany() : null;
      task.setUnitPrice(
          (BigDecimal) productCompanyService.get(saleOrderLine.getProduct(), "salePrice", company));
    }
    task.setDescription(saleOrderLine.getDescription());
    task.setQuantity(saleOrderLine.getQty());
    task.setSaleOrderLine(saleOrderLine);
    task.setToInvoice(
        saleOrderLine.getSaleOrder() != null
            ? saleOrderLine.getSaleOrder().getToInvoiceViaTask()
            : false);
    return task;
  }

  @Override
  public ProjectTask create(
      TaskTemplate template, Project project, LocalDateTime date, BigDecimal qty) {
    ProjectTask task = create(template.getName(), project, template.getAssignedTo());

    task.setTaskDate(date.toLocalDate());
    task.setTaskEndDate(date.plusHours(template.getDuration().longValue()).toLocalDate());

    BigDecimal plannedHrs = template.getTotalPlannedHrs();
    if (template.getIsUniqueTaskForMultipleQuantity() && qty.compareTo(BigDecimal.ONE) > 0) {
      plannedHrs = plannedHrs.multiply(qty);
      task.setName(task.getName() + " x" + qty.intValue());
    }
    task.setTotalPlannedHrs(plannedHrs);

    return task;
  }

  @Override
  public ProjectTask updateDiscount(ProjectTask projectTask) {
    PriceList priceList = projectTask.getProject().getPriceList();
    if (priceList == null) {
      this.emptyDiscounts(projectTask);
      return projectTask;
    }

    PriceListLine priceListLine =
        this.getPriceListLine(projectTask, priceList, projectTask.getUnitPrice());
    Map<String, Object> discounts =
        priceListService.getReplacedPriceAndDiscounts(
            priceList, priceListLine, projectTask.getUnitPrice());

    if (discounts == null) {
      this.emptyDiscounts(projectTask);
    } else {
      projectTask.setDiscountTypeSelect((Integer) discounts.get("discountTypeSelect"));
      projectTask.setDiscountAmount((BigDecimal) discounts.get("discountAmount"));
      if (discounts.get("price") != null) {
        projectTask.setPriceDiscounted((BigDecimal) discounts.get("price"));
      }
    }
    return projectTask;
  }

  private void emptyDiscounts(ProjectTask projectTask) {
    projectTask.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
    projectTask.setDiscountAmount(BigDecimal.ZERO);
    projectTask.setPriceDiscounted(BigDecimal.ZERO);
  }

  private PriceListLine getPriceListLine(
      ProjectTask projectTask, PriceList priceList, BigDecimal price) {

    return priceListService.getPriceListLine(
        projectTask.getProduct(), projectTask.getQuantity(), priceList, price);
  }

  @Override
  public ProjectTask compute(ProjectTask projectTask) {
    if (projectTask.getProduct() == null && projectTask.getProject() == null
        || projectTask.getUnitPrice() == null
        || projectTask.getQuantity() == null) {
      return projectTask;
    }
    BigDecimal priceDiscounted = this.computeDiscount(projectTask);
    BigDecimal exTaxTotal = this.computeAmount(projectTask.getQuantity(), priceDiscounted);

    projectTask.setPriceDiscounted(priceDiscounted);
    projectTask.setExTaxTotal(exTaxTotal);

    return projectTask;
  }

  private BigDecimal computeDiscount(ProjectTask projectTask) {

    return priceListService.computeDiscount(
        projectTask.getUnitPrice(),
        projectTask.getDiscountTypeSelect(),
        projectTask.getDiscountAmount());
  }

  private BigDecimal computeAmount(BigDecimal quantity, BigDecimal price) {

    BigDecimal amount =
        price
            .multiply(quantity)
            .setScale(AppSaleService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_EVEN);

    return amount;
  }

  @Override
  public List<InvoiceLine> createInvoiceLines(
      Invoice invoice, List<ProjectTask> projectTaskList, int priority) throws AxelorException {

    List<InvoiceLine> invoiceLineList = new ArrayList<>();
    int count = 0;
    for (ProjectTask projectTask : projectTaskList) {
      invoiceLineList.addAll(this.createInvoiceLine(invoice, projectTask, priority * 100 + count));
      count++;
    }
    return invoiceLineList;
  }

  @Override
  public List<InvoiceLine> createInvoiceLine(Invoice invoice, ProjectTask projectTask, int priority)
      throws AxelorException {

    InvoiceLineGenerator invoiceLineGenerator =
        new InvoiceLineGenerator(
            invoice,
            projectTask.getProduct(),
            projectTask.getName(),
            projectTask.getUnitPrice(),
            BigDecimal.ZERO,
            projectTask.getPriceDiscounted(),
            projectTask.getDescription(),
            projectTask.getQuantity(),
            projectTask.getUnit(),
            null,
            priority,
            projectTask.getDiscountAmount(),
            projectTask.getDiscountTypeSelect(),
            projectTask.getExTaxTotal(),
            BigDecimal.ZERO,
            false) {

          @Override
          public List<InvoiceLine> creates() throws AxelorException {

            InvoiceLine invoiceLine = this.createInvoiceLine();
            invoiceLine.setProject(projectTask.getProject());
            invoiceLine.setSaleOrderLine(projectTask.getSaleOrderLine());
            projectTask.setInvoiceLine(invoiceLine);

            List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
            invoiceLines.add(invoiceLine);

            return invoiceLines;
          }
        };

    return invoiceLineGenerator.creates();
  }

  @Override
  protected void updateModuleFields(ProjectTask projectTask, ProjectTask nextProjectTask) {
    super.updateModuleFields(projectTask, nextProjectTask);

    // Module 'business project' fields
    nextProjectTask.setToInvoice(projectTask.getToInvoice());
    nextProjectTask.setExTaxTotal(projectTask.getExTaxTotal());
    nextProjectTask.setDiscountTypeSelect(projectTask.getDiscountTypeSelect());
    nextProjectTask.setDiscountAmount(projectTask.getDiscountAmount());
    nextProjectTask.setPriceDiscounted(projectTask.getPriceDiscounted());
    nextProjectTask.setInvoicingType(projectTask.getInvoicingType());
    nextProjectTask.setCustomerReferral(projectTask.getCustomerReferral());
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  @Override
  public ProjectTask updateTask(ProjectTask projectTask, AppBusinessProject appBusinessProject)
      throws AxelorException {

    projectTask = computeDefaultInformation(projectTask);

    if (projectTask.getInvoicingType() == ProjectTaskRepository.INVOICING_TYPE_PACKAGE
        && !projectTask.getIsTaskRefused()) {

      Pattern pattern = Pattern.compile(", ");

      switch (projectTask.getProject().getInvoicingSequenceSelect()) {
        case ProjectRepository.INVOICING_SEQ_INVOICE_PRE_TASK:
          projectTask.setToInvoice(
              !Strings.isNullOrEmpty(appBusinessProject.getPreTaskStatusSet())
                  && pattern
                      .splitAsStream(appBusinessProject.getPreTaskStatusSet())
                      .map(Integer::valueOf)
                      .collect(Collectors.toList())
                      .contains(projectTask.getStatus()));
          break;

        case ProjectRepository.INVOICING_SEQ_INVOICE_POST_TASK:
          projectTask.setToInvoice(
              !Strings.isNullOrEmpty(appBusinessProject.getPostTaskStatusSet())
                  && pattern
                      .splitAsStream(appBusinessProject.getPostTaskStatusSet())
                      .map(Integer::valueOf)
                      .collect(Collectors.toList())
                      .contains(projectTask.getStatus()));
          break;
      }
    } else {
      projectTask.setToInvoice(
          projectTask.getInvoicingType() == ProjectTaskRepository.INVOICING_TYPE_TIME_SPENT);
    }

    return projectTaskRepo.save(projectTask);
  }

  @Override
  public ProjectTask computeDefaultInformation(ProjectTask projectTask) throws AxelorException {

    Product product = projectTask.getProduct();
    if (product != null) {
      projectTask.setInvoicingType(ProjectTaskRepository.INVOICING_TYPE_PACKAGE);
      if (projectTask.getUnitPrice() == null
          || projectTask.getUnitPrice().compareTo(BigDecimal.ZERO) == 0) {
        projectTask.setUnitPrice(this.computeUnitPrice(projectTask));
      }
    } else {
      TeamTaskCategory teamTaskCategory = projectTask.getTeamTaskCategory();
      if (teamTaskCategory == null) {
        return projectTask;
      }

      projectTask.setInvoicingType(teamTaskCategory.getDefaultInvoicingType());
      projectTask.setProduct(teamTaskCategory.getDefaultProduct());
      product = projectTask.getProduct();
      if (product == null) {
        return projectTask;
      }
      projectTask.setUnitPrice(this.computeUnitPrice(projectTask));
    }
    Company company =
        projectTask.getProject() != null ? projectTask.getProject().getCompany() : null;
    Unit salesUnit = (Unit) productCompanyService.get(product, "salesUnit", company);
    projectTask.setUnit(
        salesUnit != null ? salesUnit : (Unit) productCompanyService.get(product, "unit", company));
    projectTask.setCurrency((Currency) productCompanyService.get(product, "saleCurrency", company));
    projectTask.setQuantity(projectTask.getBudgetedTime());

    projectTask = this.updateDiscount(projectTask);
    projectTask = this.compute(projectTask);
    return projectTask;
  }

  private BigDecimal computeUnitPrice(ProjectTask projectTask) throws AxelorException {
    Product product = projectTask.getProduct();
    Company company =
        projectTask.getProject() != null ? projectTask.getProject().getCompany() : null;
    BigDecimal unitPrice = (BigDecimal) productCompanyService.get(product, "salePrice", company);

    PriceList priceList =
        partnerPriceListService.getDefaultPriceList(
            projectTask.getProject().getClientPartner(), PriceListRepository.TYPE_SALE);
    if (priceList == null) {
      return unitPrice;
    }

    PriceListLine priceListLine = this.getPriceListLine(projectTask, priceList, unitPrice);
    Map<String, Object> discounts =
        priceListService.getReplacedPriceAndDiscounts(priceList, priceListLine, unitPrice);

    if (discounts == null) {
      return unitPrice;
    } else {
      unitPrice =
          priceListService.computeDiscount(
              unitPrice,
              (Integer) discounts.get("discountTypeSelect"),
              (BigDecimal) discounts.get("discountAmount"));
    }
    return unitPrice;
  }

  @Override
  public ProjectTask resetProjectTaskValues(ProjectTask projectTask) {
    projectTask.setProduct(null);
    projectTask.setInvoicingType(null);
    projectTask.setToInvoice(null);
    projectTask.setQuantity(null);
    projectTask.setUnit(null);
    projectTask.setUnitPrice(null);
    projectTask.setCurrency(null);
    projectTask.setExTaxTotal(null);
    return projectTask;
  }
}
