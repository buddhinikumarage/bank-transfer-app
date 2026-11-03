package com.example.bankapp.service;

import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankService {

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    public List<Customer> getAllCustomers(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return customerRepository.searchByName(keyword.trim());
        }
        return customerRepository.findAllOrdered();
    }

    public BigDecimal getTotalBalance() {
        BigDecimal total = customerRepository.getTotalBalance();
        return total != null ? total : BigDecimal.ZERO;
    }

    public List<BankTransaction> getLatestTransactions() {
        return transactionRepository.findAllLatest();
    }

    public List<BankTransaction> getAccountHistory(String accountNumber) {
        return transactionRepository.findHistory(accountNumber);
    }

    public long getSuccessCount() {
        return transactionRepository.countByStatus(TransactionStatus.SUCCESS);
    }

    public long getFailedCount() {
        return transactionRepository.countByStatus(TransactionStatus.FAILED);
    }

    @Transactional
    public Customer createCustomer(Customer customer) {
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0) {
            throw new BankException("Account number " + customer.getAccountNumber() + " already exists");
        }
        if (customerRepository.countEmails(customer.getEmail()) > 0) {
            throw new BankException("Email " + customer.getEmail() + " already exists");
        }
        return customerRepository.save(customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(String fromAccount, String toAccount, BigDecimal amount, boolean simulateFailure) {
        // Step 1: Validation
        if (fromAccount == null || toAccount == null || fromAccount.trim().equalsIgnoreCase(toAccount.trim())) {
            throw new BankException("Sender and receiver must be different accounts");
        }
        customerRepository.getByAccountNumber(fromAccount)
                .orElseThrow(() -> new AccountNotFoundException("Account " + fromAccount + " not found"));
        customerRepository.getByAccountNumber(toAccount)
                .orElseThrow(() -> new AccountNotFoundException("Account " + toAccount + " not found"));

        // Step 2: Atomic Debit
        int debited = customerRepository.debit(fromAccount, amount);
        if (debited == 0) {
            throw new InsufficientBalanceException("Insufficient balance in account " + fromAccount);
        }

        // Step 3: Simulated crash checkbox
        if (simulateFailure) {
            throw new RuntimeException("Simulated system failure after the withdrawal");
        }

        // Step 4: Credit receiver
        customerRepository.credit(toAccount, amount);

        // Step 5: Save SUCCESS record
        BankTransaction tx = BankTransaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .description("Transfer completed")
                .createdAt(LocalDateTime.now())
                .build();

        return transactionRepository.save(tx);
    }
}