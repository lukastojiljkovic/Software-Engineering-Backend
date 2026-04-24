package rs.raf.banka2_bek.interbank.model;

/*
================================================================================
 TODO — SMER MEDJUBANKARSKE PORUKE
 Zaduzen: BE tim
--------------------------------------------------------------------------------
 OUTBOUND — Mi smo poslali, drugoj banci.
 INBOUND  — Druga banka nam je poslala.
================================================================================
*/
public enum InterbankMessageDirection {
    OUTBOUND,
    INBOUND
}
