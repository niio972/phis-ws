//**********************************************************************************************
//                                       ResponseFormCall.java 
// SILEX-PHIS
// Copyright © INRA 2018
// Contact: alice.boizet@inra.fr, anne.tireau@inra.fr, pascal.neveu@inra.fr
//***********************************************************************************************
package phis2ws.service.view.brapi.form;

import java.util.ArrayList;
import phis2ws.service.view.brapi.Metadata;
import phis2ws.service.view.brapi.results.ResultCall;
import phis2ws.service.view.manager.ResultForm;
import phis2ws.service.view.model.phis.Call;
import phis2ws.service.view.brapi.Status;

/**
 * Formating the result of the request about Calls
 *
 * @author Alice Boizet <alice.boizet@inra.fr>
 */
public class ResponseFormCall extends ResultForm<Call> {

    /**
     * Initialize metadata and result fields
     *
     * @param pageSize number of results per page
     * @param currentPage requested page
     * @param list list of calls
     * @param paginate
     * @param statuslist
     */
    public ResponseFormCall(int pageSize, int currentPage, ArrayList<Call> list, boolean paginate, ArrayList<Status> statuslist) {
        metadata = new Metadata(pageSize, currentPage, list.size(), statuslist);
        if (list.size() > 1) {
            result = new ResultCall(list, metadata.getPagination(), paginate);
        } else {
            result = new ResultCall(list);
        }
    }
}
