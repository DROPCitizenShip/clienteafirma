package es.gob.afirma.cliente.utilidades.exp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;

import es.gob.afirma.exceptions.AOException;

/**
 * @deprecated Usar filtros compatibles RFC2254
 */
@Deprecated
final class CompoundCondition extends AClause implements ConditionConstants {

	public CompoundCondition(String str) throws AOException {
		// Matcher simpleConditionMatcher = simpleConditionPattern.matcher(str);
		Matcher nexusConditionMatcher = nexusConditionPattern.matcher(str);

		Collection<Clause> cClauses = new ArrayList<Clause>();
		Collection<Nexus> cNexus = new ArrayList<Nexus>();

		if (nexusConditionMatcher.find()) {
			int pos = 1;
			int ini = 0, fin = 0;

			String clause;
			while (nexusConditionMatcher.find(pos)) {
				ini = nexusConditionMatcher.start();
				fin = nexusConditionMatcher.end();
				String myNexus = str.substring(ini, fin);
				cNexus.add(myNexus.equals("&&") ? Nexus.AND : Nexus.OR);

				clause = str.substring(pos, ini);
				cClauses.add(new SimpleCondition(clause));
				pos = fin;
			}
			clause = str.substring(fin, str.length() - 1);
			cClauses.add(new SimpleCondition(clause));
		} else {
			String clause = str.substring(1, str.length() - 1);
			cClauses.add(new SimpleCondition(clause));
		}

		clauses = cClauses.toArray(new Clause[cClauses.size()]);
		nexus = cNexus.toArray(new Nexus[cNexus.size()]);
	}
}
