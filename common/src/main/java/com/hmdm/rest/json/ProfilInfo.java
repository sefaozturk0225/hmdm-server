package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfilInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String  adSoyad;
    private Integer yas;
    private String  meslek;
    private String  cihazAdi;

    public String getAdSoyad() { return adSoyad; }
    public void setAdSoyad(String adSoyad) { this.adSoyad = adSoyad; }

    public Integer getYas() { return yas; }
    public void setYas(Integer yas) { this.yas = yas; }

    public String getMeslek() { return meslek; }
    public void setMeslek(String meslek) { this.meslek = meslek; }

    public String getCihazAdi() { return cihazAdi; }
    public void setCihazAdi(String cihazAdi) { this.cihazAdi = cihazAdi; }
}
