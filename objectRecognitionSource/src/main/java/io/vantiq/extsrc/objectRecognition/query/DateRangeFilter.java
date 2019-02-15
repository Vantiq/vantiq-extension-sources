package io.vantiq.extsrc.objectRecognition.query;

import java.io.File;
import java.io.FilenameFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class DateRangeFilter implements FilenameFilter {
    Date beforeDate = null;
    Date afterDate = null;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss");
    
    public DateRangeFilter(List<Date> dateRange) {
        beforeDate = dateRange.get(0);
        afterDate = dateRange.get(1);
    }

    @Override
    public boolean accept(File dir, String name) {
        try {
            name = name.replaceAll("\\s*\\([^\\)]*\\)\\s*", "");
            Date fName = format.parse(name);
            return ((beforeDate == null) || !fName.before(beforeDate)) && ((afterDate == null) || !fName.after(afterDate));
        } catch (ParseException e) {
            return false;
        }
    }

}
