def template(weights):
    fields = ['.url', '.keywords', '.title', '.body', '.inlink']
    out = open('queriesNew.txt', 'w')
    with open('queries.txt', 'r') as f:
        lines = f.readlines()
        for line in lines:
            [index, query] = line.strip().split(":")
            out.write(str(index) + ":#AND(")
            terms = query.split(' ')
            outString = []
            for i in range(len(terms)):
                string = "#WSUM("
                temp = map(lambda x: str(x) + " " + terms[i], weights)
                temp = zip(temp, fields)
                temp = map(lambda (x, y): x + y, temp)
                string += ' '.join(temp) + ")"
                outString.append(string)
            out.write(" ".join(outString) + ")\n")

if __name__ == '__main__':
    weights = [0.1, 0.1, 0.1, 0.9, 0.1]
    template(weights)
