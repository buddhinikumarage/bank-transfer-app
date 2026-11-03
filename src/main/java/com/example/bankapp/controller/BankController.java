
package com.example.bankapp.controller;

import com.example.bankapp.DTO.TransferRequest;
import com.example.bankapp.model.Customer;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final TransactionAuditService auditService;

    @GetMapping("/")
    public String dashboard(
            @RequestParam(required = false) String keyword,
            Model model) {

        populateDashboard(model, keyword);

        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest", new TransferRequest());
        }

        if (!model.containsAttribute("newCustomer")) {
            model.addAttribute("newCustomer", new Customer());
        }

        return "bank";
    }

    private void populateDashboard(Model model, String keyword) {

        model.addAttribute("customers",
                bankService.getAllCustomers(keyword));

        model.addAttribute("totalBalance",
                bankService.getTotalBalance());

        model.addAttribute("latestTransactions",
                bankService.getLatestTransactions());

        model.addAttribute("successCount",
                bankService.getSuccessCount());

        model.addAttribute("failedCount",
                bankService.getFailedCount());

        model.addAttribute("keyword", keyword);
    }

    @PostMapping("/customers")
    public String addCustomer(
            @Valid @ModelAttribute("newCustomer") Customer customer,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {

            populateDashboard(model, null);
            model.addAttribute("transferRequest", new TransferRequest());

            return "bank";
        }

        try {

            bankService.createCustomer(customer);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Customer created successfully!");

        } catch (Exception ex) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/transfer")
    public String transferMoney(
            @Valid @ModelAttribute("transferRequest") TransferRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        // 1. Check validation errors
        if (bindingResult.hasErrors()) {

            populateDashboard(model, null);

            model.addAttribute("newCustomer", new Customer());

            return "bank";
        }

        try {

            // 2. Pass the complete TransferRequest object
            bankService.transfer(request);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Transfer completed successfully!");

        } catch (Exception e) {

            // 3. Record failed transaction
            auditService.recordFailure(
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    e.getMessage()
            );

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Transfer failed: " + e.getMessage()
            );
        }

        // 4. Redirect to dashboard
        return "redirect:/";
    }

    @GetMapping("/accounts/{accountNumber}/history")
    public String accountHistory(
            @PathVariable String accountNumber,
            Model model) {

        model.addAttribute("accountNumber", accountNumber);

        model.addAttribute(
                "history",
                bankService.getAccountHistory(accountNumber));

        return "history";
    }
}
