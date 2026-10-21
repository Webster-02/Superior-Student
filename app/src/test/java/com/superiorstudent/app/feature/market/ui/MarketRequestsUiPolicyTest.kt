package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketRequestsUiPolicyTest {
    private val samples=listOf(
        sample("design","Design","BSOPM","Poster for society",3,1500,0),
        sample("notes","Notes","Anatomy","Ocular notes",5,0,1),
        sample("coding","Coding",null,"Python lab help",1,0,2)
    )
    @Test fun categoryChipFiltersToMatchingRequests() {
        assertEquals(listOf("notes"),MarketRequestsUiPolicy.filterAndSort(samples,"Notes","",MarketRequestSort.REFERENCE_ORDER).map{it.id})
    }
    @Test fun searchMatchesTitleCategoryAndCourseCaseInsensitively() {
        assertEquals(listOf("design"),MarketRequestsUiPolicy.filterAndSort(samples,"All","POSTER",MarketRequestSort.REFERENCE_ORDER).map{it.id})
        assertEquals(listOf("notes"),MarketRequestsUiPolicy.filterAndSort(samples,"All","anatomy",MarketRequestSort.REFERENCE_ORDER).map{it.id})
    }
    @Test fun deadlineSortShowsSoonestFirst() {
        assertEquals(listOf("coding","design","notes"),MarketRequestsUiPolicy.filterAndSort(samples,"All","",MarketRequestSort.DEADLINE).map{it.id})
    }
    @Test fun emptyCategoryResultIsEmpty() {
        assertEquals(emptyList<String>(),MarketRequestsUiPolicy.filterAndSort(samples,"Tutoring","",MarketRequestSort.REFERENCE_ORDER).map{it.id})
    }
    private fun sample(id:String,category:String,course:String?,title:String,dueDays:Int,budget:Int,order:Int)=MarketRequestPreview(
        id,category,course,title,"Sample description","Budget",budget,"Due",dueDays,0,"Sample Student","SS","· now",false,0,order
    )
}
