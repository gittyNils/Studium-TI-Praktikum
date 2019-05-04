import java.util.*;

public class CompilerHelper 
{
	// Programmname
	private String progName;
	
	// Erzeugtes URM-Programm
	private ArrayList<String> program = new ArrayList<String>();
	
	// Mapping zwischen den Variablen aus While zu den Registern in URM.
	// --> Symboltabelle
	private HashMap<String, String > identMap = new HashMap<String, String >();
	
	
	// Merker für das nächste noch unbenutzte Register.
	// R1 ist reserviert für das Ausgaberegister.
	private int nextFreeRegister = 2;
	
	// Stack zum Merken der Sprungmarken.
	// Erst wird die Zeilennummer drauf gelegt, in der die Sprungmarke für den Sprung zum Ende des Whiles ersetzt werden muss.
	// Dann wird die Zeilennummer drauf gelegt, zu der man springen muss, wenn man die While-Bedingung wieder oben im Kopf prüfen will.
	private Stack<Integer> whileSprungmarken = new Stack<Integer>();
	
	
	
	// Setzen des Programmnamens
	public void SetProgName(String progName)
	{
		this.progName = progName;
	}
	
	
	// --> Variablen-Mapping -----------------------------------------------------------------------------
	
	
	// Füge ein Register für eine Variable hinzu
	public void AddIdent(String variable) throws ParseException
	{
		if(!identMap.containsKey(variable))
		{
			identMap.put(variable, GetUnusedRegister());
		}
		else
		{
			throw new ParseException(String.format("%s doppelt deklariert", variable));
		}
	}
	
	
	// Füge ein Register für die out-Variable hinzu
	public void AddOutIdent(String variable) throws ParseException
	{
		if(!identMap.containsKey(variable))
		{
			identMap.put(variable, "R1");
		}
		else
		{
			throw new ParseException(String.format("out-Variable %s doppelt deklariert", variable));
		}
	}

	
	// Liefert das Register zur übergebenen Variable
	public String GetRegister(String variable) throws ParseException
	{
		String ret = null;
		
		if(identMap.containsKey(variable))
		{
			ret = identMap.get(variable);
		}
		else
		{
			throw new ParseException("Variable " + variable + " nicht definiert.");
		}

		return ret;
	}
	
	// Liefert ein bisher nicht genutztes Register
	public String GetUnusedRegister()
	{
		String ret = String.format("R%d", nextFreeRegister);
		nextFreeRegister++;
		return ret;
	}

	
	
	// Fügt einen Kommentar im URM-Programm ein, der das Mapping zwischen den Variablen und den Registern anzeigt
	public void AddIdentMapAsComment()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("; ");
		
		for(Map.Entry<String, String> entry : identMap.entrySet())
		{
			sb.append(entry.getKey() + "->" + entry.getValue() + ", ");
		}
		
		// in Programm einfügen
		program.add(sb.toString());
	}
	
	
	// END Variablen-Mapping -----------------------------------------------------------------------------
	
	// Liefert die Nummer der nächsten Zeile, wobei Kommentarzeilen keine Zeilennummer bekommen.
	private int GetNextLineNumber()
	{
		int lineNr = 1;
		for(String line : program)
		{
			if(!line.startsWith(";"))
			{
				lineNr++;
			}
		}
		return lineNr;
	}
	

	
	// --> Anweisungen überführen -----------------------------------------------------------------------------
	
	
	// Überführe Var = 0 nach URM
	public void AssignZero(String variable) throws ParseException
	{
		program.add(String.format("; %s = 0", variable));
		
		
		String register = GetRegister(variable);
		program.add(String.format("%s = 0", register));
	}
	
	// Überführe Var1 = Var2 + 1 bzw. Var1 = Var1 + 1 nach URM
	public void AssignPlusOne(String variableLeft, String variableRight) throws ParseException
	{
		program.add(String.format("; %s = %s + 1", variableLeft, variableRight));
		
		String registerLeft = GetRegister(variableLeft);
		String registerRight = GetRegister(variableRight);
		
		CopyMakro(registerLeft, registerRight);
		program.add(String.format("%s++", registerLeft));
	}
	
	
	// Copy-Makro in URM in Programme einfügen
	// kopiert y auf x
	private void CopyMakro(String xRegister, String yRegister) throws ParseException
	{
		// Das Copy-Makro funktioniert nicht, wenn ein Register auf sich selbst kopiert wird.
		// Somit das Makro so abwandeln, dass bei Gleichheit der Register kein Kopieren erfolgt.
		if(xRegister.equals(yRegister))
		{
			program.add(String.format("; CopyMakro eingespart, da Ziel- und Quell-Register identisch."));
		}
		else
		{
			// aus dem Makro lokales Register z
			String zRegister = GetUnusedRegister();
			
			// Erste Zeile des Makros merken
			int firstLine = GetNextLineNumber();
					
			
			program.add(String.format("%s = 0", xRegister));
			program.add(String.format("if %s == 0 goto %d", yRegister, firstLine + 5));
			program.add(String.format("%s--", yRegister));
			program.add(String.format("%s++", zRegister));
			program.add(String.format("goto %d", firstLine + 1));
			program.add(String.format("if %s == 0 goto %d", zRegister, firstLine + 10));
			program.add(String.format("%s--", zRegister));
			program.add(String.format("%s++", xRegister));
			program.add(String.format("%s++", yRegister));
			program.add(String.format("goto %d", firstLine + 5));
		}
	}
	
	
	
	// überführe while V1 != V2 do begin nach URM (das end folgt in einer anderen Funktion)
	// Hier ist die Zeilennummer des end noch nicht bekannt. Somit muss erst mal eine Dummy-Zeilennummer eingetragen werden, die später ersetzt wird.
	public void WhileBegin(String variableLeft, String variableRight) throws ParseException
	{
		program.add(String.format("; while %s != %s do begin", variableLeft, variableRight));
		
		// neue Register für die kopierten Variablen
		// Wir brauchen eine Kopie von den Variablen, da wir diese verändern werden.
		String variableLeftCopyRegister = GetUnusedRegister();
		String variableRightCopyRegister = GetUnusedRegister();

		// Merken der ersten Zeile 
		int firstLine = GetNextLineNumber();
		
		// Nun echt Kopieren
		CopyMakro(variableLeftCopyRegister, GetRegister(variableLeft));
		CopyMakro(variableRightCopyRegister, GetRegister(variableRight));

		// Merken der Zeilennummer für die erste Anweisung nach dem Copy
		int lineAfterCopy = GetNextLineNumber();
		
		// Block für While-Kopf-Logik:
		program.add(String.format("if %s == 0 goto %s", variableLeftCopyRegister, lineAfterCopy + 2));
		program.add(String.format("goto %s", lineAfterCopy + 4));

		// Merken wir uns, in welcher Zeile wir die Sprungmarke nachher erstzen müssen.
		// (Das ist die Sprungmake für hinter While springen)
		// Aber Trick: Merke nicht die Zeilennummer, sondern den Index der Zeile im Programm (kann wegen Kommentaren abweichen von ersterem).
		int lineToReplace = program.size();
		program.add(String.format("if %s == 0 goto #Sprungmarke#", variableRightCopyRegister));
		program.add(String.format("goto %s", lineAfterCopy + 8));
		program.add(String.format("if %s == 0 goto %s", variableRightCopyRegister, lineAfterCopy + 8));
		program.add(String.format("%s--", variableLeftCopyRegister));
		program.add(String.format("%s--", variableRightCopyRegister));
		program.add(String.format("goto %d", lineAfterCopy));


		// Erst wird die Zeilennummer drauf gelegt, in der die Sprungmarke für den Sprung zum Ende des Whiles ersetzt werden muss.
		// Dann wird die Zeilennummer drauf gelegt, zu der man springen muss, wenn man die While-Bedingung wieder oben im Kopf prüfen will.
		whileSprungmarken.push(lineToReplace);
		whileSprungmarken.push(firstLine);
	}
	
	
	// Ende eines Whiles in URM-Programm einfügen.
	// Dafür müssen noch Sprungmarken gesetzt werden.
	public void WhileEnd()
	{
		program.add(String.format("; while end"));
		
		int lineNumberBeginWhile = whileSprungmarken.pop();
		int indexWhereToReplaceSprungmarke = whileSprungmarken.pop();
		
		program.add(String.format("goto %d", lineNumberBeginWhile));
		
		String lineToChange = program.get(indexWhereToReplaceSprungmarke);
		String changedLine = lineToChange.replace("#Sprungmarke#", Integer.toString(GetNextLineNumber()));
		program.set(indexWhereToReplaceSprungmarke, changedLine);
	}
	
	
	// END Anweisungen überführen -----------------------------------------------------------------------------
	
	
	
	
	
	
	// --> Ergebnis-Programm -----------------------------------------------------------------------------
	
	// Ausgabe mit Zeilennummern
	public void PrintProgram()
	{
		int lineNumer = 1;
		for(String line : program)
		{
			if(line.startsWith(";"))
			{
				// Kommentare bekommen bei der Art der Ausgabe keine Zeilennummer
				System.out.println(String.format("    %s", line));
			}
			else
			{
				System.out.println(String.format("%03d: %s", lineNumer++, line));
			}
		}
	}
	
	// Ausgabe für URM-Simulator
	public void PrintProgramForSimulator()
	{
		for(String line : program)
		{
			System.out.println(line);
		}
	}
	
	
	// END Ergebnis-Programm -----------------------------------------------------------------------------
	
}
