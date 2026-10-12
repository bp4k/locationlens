package net.bp4k.locationlens.repo;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import net.bp4k.locationlens.entity.User;


/**
 * DAO for Users.
 * @author Advaith Menon
 */
public interface UserRepository extends JpaRepository<User, UUID> {}
