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
            Date fName = format.parse(name);
            System.out.print(name);
            if (beforeDate == null) {
                System.out.print("Before Date == null");
            } else if (!fName.after(beforeDate)) {
                System.out.print("Name is not after 'before date'");
            } else if (afterDate == null) {
                System.out.print("After Date == null");
            } else if (!fName.before(afterDate)) {
                System.out.print("Name is not before 'after date'");
            }
            return ((beforeDate == null) || !fName.after(beforeDate)) && ((afterDate == null) || !fName.before(afterDate));
        } catch (ParseException e) {
            return false;
        }
    }

}
