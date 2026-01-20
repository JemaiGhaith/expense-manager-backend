package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.entities.Category;
import com.coralio.expense_management_microservice.repos.CategoryRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category createCategory(Category category) {
        return categoryRepository.save(category);
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }



    public Category updateCategory(Long id, Category categoryDetails) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id " + id));
        category.setName(categoryDetails.getName());
        category.setPlafond(categoryDetails.getPlafond());
        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
}
