package bnlp.re.sparc;

import org.apache.commons.csv.CSVRecord;

/**
 * Created by bozyurt on 9/18/20.
 */
public class ConnectivityAnnotationRecord {
    String pmid;
    String doi;
    String structure1;
    String structure2;
    String structure3;
    String structure4;
    String claim;
    String sectionFound;
    String species;
    String sex;

    public ConnectivityAnnotationRecord(String pmid, String doi) {
        this.pmid = pmid;
        this.doi = doi;
    }


    public static ConnectivityAnnotationRecord fromCSV(CSVRecord record) {
        String pmid = record.get(0);
        String doi = record.get(1);
        ConnectivityAnnotationRecord car = new ConnectivityAnnotationRecord(pmid, doi);
        car.structure1 = record.get(2);
        car.structure2 = record.get(3);
        car.structure3 = record.get(4);
        car.structure4 = record.get(5);
        car.claim = record.get(6);
        car.sectionFound = record.get(7);
        car.species = record.get(8);
        car.sex = record.get(9);
        return car;
    }

    public String getPmid() {
        return pmid;
    }

    public String getDoi() {
        return doi;
    }

    public String getStructure1() {
        return structure1;
    }

    public String getStructure2() {
        return structure2;
    }

    public String getStructure3() {
        return structure3;
    }

    public String getStructure4() {
        return structure4;
    }

    public String getClaim() {
        return claim;
    }

    public String getSectionFound() {
        return sectionFound;
    }

    public String getSpecies() {
        return species;
    }

    public String getSex() {
        return sex;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ConnectivityAnnotationRecord{");
        sb.append("pmid='").append(pmid).append('\'');
        sb.append(", doi='").append(doi).append('\'');
        sb.append(", structure1='").append(structure1).append('\'');
        sb.append(", structure2='").append(structure2).append('\'');
        sb.append(", structure3='").append(structure3).append('\'');
        sb.append(", structure4='").append(structure4).append('\'');
        sb.append(", claim='").append(claim).append('\'');
        sb.append(", sectionFound='").append(sectionFound).append('\'');
        sb.append(", species='").append(species).append('\'');
        sb.append(", sex='").append(sex).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
