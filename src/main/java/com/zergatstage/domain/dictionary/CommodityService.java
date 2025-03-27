package com.zergatstage.domain.dictionary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CommodityService {
    private final CommodityRepository repository;

    public CommodityService(CommodityRepository repository) {
        this.repository = repository;
    }

    /**
     * Retrieves a commodity by ID. Throws if not found.
     *
     * @param name The commodity name.
     * @return The commodity.
     * @throws NoSuchElementException If not found.
     */
    public Commodity getCommodityByName(String name) {
        return repository.findByName(name).orElseThrow();
    }

    /**
     * Retrieves a commodity by ID. Throws if not found.
     *
     * @param id The commodity ID.
     * @return The commodity.
     * @throws NoSuchElementException If not found.
     */
    public Commodity getCommodity(String id) {
        return repository.findById(id).orElseThrow();
    }

    public String[] getAllNames(){
        return repository.findAll().stream()
                .map(Commodity::getName)
                .toArray(String[]::new);
    }

    /**
     * Retrieves a commodity by ID or creates it if not found.
     *
     * @param id       The unique ID of the commodity.
     * @param name     The name of the commodity (used only if new).
     * @param category The category of the commodity (used only if new).
     * @return Existing or newly created Commodity.
     */
    @Transactional
    public Commodity getOrAddCommodity(String id, String name, String category) {
        Optional<Commodity> existing = repository.findById(id);
        if (existing.isPresent()) {
            return existing.get();
        }
        Commodity newCommodity = Commodity.builder().id(id).name(name).category(category).build();
        return repository.save(newCommodity);
    }

}
