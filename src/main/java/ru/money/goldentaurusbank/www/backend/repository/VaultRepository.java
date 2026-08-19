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
    

    Page<Vault> findByUserAndArchivedFalse(User user, Pageable pageable);


    Page<Vault> findByUserAndNameContainingIgnoreCaseAndArchivedFalse(User user, String name, Pageable pageable);

    /**
     * Достаёт хранилище вместе с архивными: этим методом история грузит то,
     * что пользователь уже удалил. Для операций нужен findByIdAndUserAndArchivedFalse.
     */
    Optional<Vault> findByIdAndUser(Long id, User user);

    Optional<Vault> findByIdAndUserAndArchivedFalse(Long id, User user);

    @Query("SELECT v FROM Vault v WHERE v.user = :user AND v.bank = :bank AND v.archived = false")
    List<Vault> findByUserAndBank(@Param("user") User user, @Param("bank") BankDictionary bank);



    @Query("SELECT v FROM Vault v LEFT JOIN v.bank b WHERE v.user = :user AND LOWER(v.name) = LOWER(:name) "
            + "AND v.archived = false "
            + "AND ((:bankId IS NULL AND b IS NULL) OR b.id = :bankId)")
    List<Vault> findByUserAndNameIgnoreCaseAndBankId(
            @Param("user") User user,
            @Param("name") String name,
            @Param("bankId") Long bankId
    );


    @EntityGraph(attributePaths = {"bullions"})
    List<Vault> findByUserAndArchivedFalse(User user);

    Optional<Vault> findVaultByUserAndVaultTypeAndArchivedFalse(User user, VaultType vaultType);

    /** Архивные хранилища тоже держат банк — удалять его нельзя и после архивации. */
    boolean existsByBank(BankDictionary bank);

}