package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.money.goldentaurusbank.www.backend.model.domain.BankDictionary;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.VaultType;

import java.util.List;
import java.util.Optional;

public interface VaultRepository extends JpaRepository<Vault, Long> {
    

    Page<Vault> findByUser(User user, Pageable pageable);
    

    Page<Vault> findByUserAndNameContainingIgnoreCase(User user, String name, Pageable pageable);
    
    Optional<Vault> findByIdAndUser(Long id, User user);

    @Query("SELECT v FROM Vault v WHERE v.user = :user AND v.bank = :bank")
    List<Vault> findByUserAndBank(@Param("user") User user, @Param("bank") BankDictionary bank);



    @Query("SELECT v FROM Vault v WHERE v.user = :user AND LOWER(v.name) = LOWER(:name) AND v.bank.id = :bankId")
    Optional<Vault> findByUserAndNameIgnoreCaseAndBank(
            @Param("user") User user,
            @Param("name") String name,
            @Param("bankId") Long bankId
    );

    @Query("SELECT v FROM Vault v WHERE v.user = :user AND LOWER(v.name) = LOWER(:name)")
    Optional<Vault> findByUserAndNameIgnoreCase(
            @Param("user") User user,
            @Param("name") String name
    );



    @EntityGraph(attributePaths = {"bullions"})
    List<Vault> findByUser(User user);

    Optional<Vault> findVaultByUserAndVaultType(User user, VaultType vaultType);

}